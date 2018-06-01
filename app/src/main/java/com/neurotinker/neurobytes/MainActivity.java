package com.neurotinker.neurobytes;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import com.felhr.utils.HexData;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.Channel;
import com.google.api.services.drive.model.File;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.UnsignedInteger;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.expandable.ExpandableExtension;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter.listeners.CustomEventHook;
import com.mikepenz.fastadapter_extensions.drag.ItemTouchCallback;
import com.mikepenz.fastadapter_extensions.drag.SimpleDragCallback;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.StringTokenizer;

import static com.neurotinker.neurobytes.NidService.ACTION_NID_DISCONNECTED;
import static com.neurotinker.neurobytes.NidService.ACTION_NID_READY;
import static com.neurotinker.neurobytes.NidService.ACTION_SEND_PAUSE;

public class MainActivity extends AppCompatActivity
        implements ChannelDisplayFragment.OnFragmentInteractionListener{

    private static final String TAG = MainActivity.class.getSimpleName();

    private UsbFlashService flashService = new UsbFlashService(this, 0x6018, 0x1d50);

    Handler timerHandler = new Handler(Looper.getMainLooper());

    private static final String[] SCOPES = { DriveScopes.DRIVE_METADATA_READONLY, DriveScopes.DRIVE_FILE };
    private GoogleSignInClient mGoogleSignInClient;
    private Bitmap mBitmapToSave;
    private com.google.api.services.drive.Drive driveService = null;
    private static boolean nidRunning;
    NidService nidService;
    private static boolean nidBound = false;

    private final BroadcastReceiver nidReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_NID_READY:
                    nidRunning = true;
                    break;
                case ACTION_NID_DISCONNECTED:
                    nidRunning = false;
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph_potential);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        /**
         * Allow main thread network connection // TODO: remove this!
         */
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        ImageView pausePlayView = (ImageView) findViewById(R.id.pauseplay_id);
        pausePlayView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendBroadcast(new Intent(ACTION_SEND_PAUSE));
            }
        });

        ImageView recordDataView = (ImageView) findViewById(R.id.record_id);
        recordDataView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                try {
//                    exportData();
//                } catch(IOException ie) {
//                    ie.printStackTrace();
//                }
                //timerHandler.postDelayed(new DelaySendRunnable(makeIdentifyMessage(0)), 1500);
                //fastAdapter.notifyAdapterDataSetChanged();
                //fastAdapter.notifyAdapterItemChanged(0, GraphItem.UpdateType.CHINFO);
            }
        });

        ImageView flashDataView = (ImageView) findViewById(R.id.flash_id);
        flashDataView.setOnClickListener(new View.OnClickListener() {

            private final String[] gdbInitSequence = {"!", "qRcmd,747020656e", "qRcmd,v", "qRcmd,73", "vAttach;1", "vFlashErase:08000000,00004000"};
            private Queue<byte[]> messageQueue = new LinkedList<>();
            private byte[] prevMessage;
            private byte[] ACK = {'+'};

            private final String elfFilename = "main.elf";
            private final Integer blocksize = 0x80;
            private final Integer textOffset = 0x10000;
            private final Integer fingerprintOffset = 0x23e00;
            private final Integer fingerprintAddress = 0x08003e00;
            private Integer timeout = 0;
            private final Integer TIMEOUT = 50;
            private boolean quitFlag = false;

            private byte[] concat(byte[] arr1, byte[] arr2) {
                byte[] bytes = new byte[arr1.length + arr2.length];
                System.arraycopy(arr1, 0, bytes, 0, arr1.length);
                System.arraycopy(arr2, 0, bytes, arr1.length, arr2.length);
                return bytes;
            }

            private byte[] concat(byte[] arr1, byte[] arr2, byte[] arr3, byte[] arr4) {
                return concat(concat(concat(arr1, arr2), arr3), arr4);
            }

            private byte[] buildFlashCommand(int address, byte[] data) {
                StringBuilder cmd = new StringBuilder("vFlashWrite:");
                cmd.append(Integer.toHexString(address));
                cmd.append(":");
                byte[] bytes = concat(cmd.toString().getBytes(), escapeChars(data));
                return bytes;
            }

            private boolean isBadChar(byte bb) {
                return (bb == 0x23 || bb == 0x24 || bb == 0x7d);
            }

            private byte[] escapeChars(byte[] bytes) {

                int numBadChars = 0;
                for (byte b : bytes) {
                    if (isBadChar(b)) {
                        numBadChars += 1;
                    }
                }
                Log.d(TAG, "num bad chars: " + Integer.toString(numBadChars));

                byte[] escapedBytes = new byte[bytes.length + numBadChars];
                for (int i = 0,j = 0; i < bytes.length; i++, j++) {
//                    if (bytes[i] > 126) Log.d("Greater", Integer.toString(i));
                    if (isBadChar(bytes[i])) {
                        escapedBytes[j] = 0x7d; // escape char
                        escapedBytes[++j] = (byte) ((bytes[i]) ^ ((byte) 0x20));
                    } else {
                        escapedBytes[j] = bytes[i];
//                        escapedBytes[j] = 0xF;
                    }
                }
                return escapedBytes;
            }

            private LinkedList<byte[]> downloadElf() {
                try {
                    URL url = new URL("https://github.com/NeuroTinker/NeuroBytes_Touch_Sensor/raw/master/FIRMWARE/bin/main.elf");
                    InputStream inStream = new BufferedInputStream(url.openStream(), 0x2400);
                    DataInputStream dataInStream = new DataInputStream(inStream);

                    int textSize = 0x1ddc;
                    int numBlocks = (textSize / blocksize);
                    int extraBlockSize = textSize % blocksize;
                    int fingerprintSize = 0xc;
                    int length = 0;
                    int fLoc = 0;

                    /**
                     * Skip to the start of the .text section
                     */
                    length = dataInStream.skipBytes(textOffset);
                    if (length != textOffset) Log.d(TAG, "only skipped " + Integer.toString(length) + " bytes");
                    fLoc += length;

                    /**
                     * Read .text content into blocks of size [blocksize]
                     */
                    byte[][] textBlocks = new byte[numBlocks][blocksize];
                    for (int i = 0; i < numBlocks; i++) {
                        length = dataInStream.read(textBlocks[i], 0, blocksize);
                        Log.d(TAG, HexData.hexToString(textBlocks[i]));
                        if (length != blocksize) {
                            Log.d(TAG, "only read " + i + "th block " + Integer.toString(length) + " bytes");
                        }
                        fLoc += length;
                    }
                    /**
                     * If there is extra .text content with size not >= [blocksize],
                     * put it into [extrablock]
                     */
                    byte[] extraBlock = new byte[extraBlockSize];
                    if (extraBlockSize > 0) {
                        length = dataInStream.read(extraBlock, 0, extraBlockSize);
                        if (length != extraBlockSize) {
                            Log.d(TAG, "only read extra block " + Integer.toString(length) + " bytes");
                        }
                        fLoc += length;
                    }

                    /**
                     * Skip to the .fingerprint section
                     */
                    dataInStream.skipBytes(fingerprintOffset - fLoc);

                    /**
                     * Read the .fingerprint section.
                     * Note: the fingerprint size is always less than [blocksize]
                     */
                    byte[] fingerprint = new byte[fingerprintSize];
                    length = dataInStream.read(fingerprint, 0, fingerprintSize);
                    if (fingerprintSize != length) {
                        Log.d(TAG, ".fingerprint load failed");
                        Log.d(TAG, "only read " + Integer.toString(length) + " bytes");
                    }

                    Log.d(TAG, "fingerprint: " + HexData.hexToString(fingerprint));

                    /**
                     * Build flash command sequence
                     */
                    LinkedList<byte[]> flashSequence = new LinkedList<>();
                    int address = 0x8000000;
                    for (int i = 0; i < numBlocks; i++) {
                        Log.d(TAG, Integer.toString(i));
                        Log.d(TAG, "address " + Integer.toHexString(address));
                        flashSequence.add(buildFlashCommand(address, textBlocks[i]));
                        address += blocksize;
                    }
                    if (extraBlockSize > 0) flashSequence.add(buildFlashCommand(address, extraBlock));
                    flashSequence.add(buildFlashCommand(fingerprintAddress, fingerprint));

                    flashSequence.add("vFlashDone".getBytes());
                    flashSequence.add("vRun;".getBytes());
//                    flashSequence.add("R".getBytes());

                    return flashSequence;

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            class GdbCallbackRunnable implements Runnable {
                private UsbFlashService flashService;
                public GdbCallbackRunnable(UsbFlashService flashService) {
                    this.flashService = flashService;
                }
                @Override
                public void run() {
                    Log.d("GDB Received", Boolean.toString(flashService.IsThereAnyReceivedData()));

                    /**
                     * Check for received packets
                     */
                    if (flashService.IsThereAnyReceivedData()) {
                        byte[] data = flashService.GetReceivedDataFromQueue();
                        String asciiData = new String(data, Charset.forName("UTF-8"));

                        if (asciiData.contains("-")) {
                            Log.d(TAG, "message failed");
                            sendPrevMessage();
                            timeout += 25;
                        } else {
                            timeout = 0;
                        }

                        /**
                         * Send ACK if packet fully received.
                         * Not bothering to even check csum or anything...
                         */
                        if (asciiData.contains("#")) {
                            flashService.WriteData(ACK);
                        }

                        /**
                         * Process packet's message content and continue message sequence
                         */
                        if (asciiData.contains("$")) {
                            String messageEncoded = asciiData.split("[$#]")[1];
                            Log.d(TAG, "GDB message received " + messageEncoded);
                            if (messageEncoded.contains("OK")) {
                                quitFlag = sendNextMessage();
                            } else if (messageEncoded.contains("T05")) {
                                // attach successful
                                quitFlag = sendNextMessage();
                            }
                        }

                    } else {
                        if (timeout++ >= TIMEOUT) {
                            flashService.StopReadingThread();
                            flashService.CloseTheDevice();
                            Log.d(TAG, "timeout");
                            quitFlag = true;
                        }
                    }
                    if (!quitFlag) timerHandler.postDelayed(this, 10);
                }
            }

            private boolean sendNextMessage() {
                if (messageQueue.isEmpty()) {
                    flashService.CloseTheDevice();
                    Log.d(TAG, "flash sequence completed");
                    return true;
                } else {
                    byte[] msg = messageQueue.remove();
                    Log.d(TAG, "sending message: " + HexData.hexToString(msg));
                    prevMessage = msg;
                    flashService.WriteData(buildPacket(msg));
                    return false;
                }
            }

            private void sendPrevMessage() {
                flashService.WriteData(buildPacket(prevMessage));
            }

            private byte[] buildPacket(byte[] msg) {
                final String startTok = "$";
                final String csumTok = "#";

                /**
                 * Calculate checksum
                 */
                Integer csum = 0;
                for (byte b : msg) {
                    csum += b;
                    byte[] tmp = {b};
                }
                csum %= 256;
                csum &= 0xFF;

                /**
                 * Build packet
                 */
                return concat(startTok.getBytes(), msg, csumTok.getBytes(), Integer.toHexString(csum).getBytes());
            }

            @Override
            public void onClick(View view) {
                for (String s : gdbInitSequence) {
                    messageQueue.add(s.getBytes());
                }
                messageQueue.addAll(downloadElf());
                /**
                 * Flash the connected NeuroBytes board with correct firmware
                 */
                flashService.OpenDevice();
                flashService.StartReadingThread();
                sendNextMessage();
                Log.d("GDB Received", Boolean.toString(flashService.IsThereAnyReceivedData()));
                GdbCallbackRunnable callback = new GdbCallbackRunnable(flashService);
//                flashService.StartReadingThread();
                timerHandler.postDelayed(callback, 10);
//                flashService.CloseTheDevice();
            }
        });
    }

    public void onFragmentInteraction(Uri uri) {
        // Test ChannelDisplayFragment interface
    }

    public boolean exportData() throws IOException {
        // get google drive authentication
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(this.SCOPES))
                .setBackOff(new ExponentialBackOff());

        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        driveService = new com.google.api.services.drive.Drive.Builder(
                transport, jsonFactory, credential)
                .setApplicationName("NeuroBytes Data Uploader")
                .build();

        File fileMetadata = new File();
        fileMetadata.setName("data.csv");
        fileMetadata.setMimeType("application/vnd.google-apps.spreadsheet");

        java.io.File filePath = new java.io.File("files/data.csv");
        FileContent mediaContent = new FileContent("text/csv", filePath);
       // File file = driveService.files().create(fileMetadata, mediaContent)
       //         .setFields("id")
       //         .execute();
        //System.out.println("File ID: " + file.getId());
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_graph_potential, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            // start the settings activity
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onResume() {
        super.onResume();
        setFilters();  // Start listening notifications from UsbService
        Log.d(TAG, "trying to bind to NidService");
        Intent bindingIntent = new Intent(this, NidService.class);
        bindService(bindingIntent, nidConnection, Context.BIND_AUTO_CREATE);


    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(nidReceiver);
        unbindService(nidConnection);
    }

    private final ServiceConnection nidConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            NidService.NidBinder binder = (NidService.NidBinder) iBinder;
            nidService = binder.getService();
            nidBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            nidBound = false;
        }
    };

//    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
//        if (!UsbService.SERVICE_CONNECTED) {
//            Intent startService = new Intent(this, service);
//            if (extras != null && !extras.isEmpty()) {
//                Set<String> keys = extras.keySet();
//                for (String key : keys) {
//                    String extra = extras.getString(key);
//                    startService.putExtra(key, extra);
//                }
//            }
//            startService(startService);
//        }
//        Intent bindingIntent = new Intent(this, service);
//        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
//    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();

        registerReceiver(nidReceiver, filter);
    }


}

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

            private final String[] gdbInitSequence = {"qRcmd,s", "vAttach;1"};
            private Queue<String> messageQueue = new LinkedList<>();
            private byte[] ACK = {'+'};

            private final String elfFilename = "main.elf";
            private final Integer blocksize = 0x80;
            private final Integer textOffset = 0x10000;
            private final Integer fingerprintOffset = 0x23e00;

            private void downloadElf() {
                try {
                    URL url = new URL("http://www.github.com/NeuroTinker/NeuroBytes_Interneuron/raw/master/FIRMWARE/bin/main.elf");
                    InputStream inStream = url.openStream();
                    DataInputStream dataInStream = new DataInputStream(inStream);

                    byte[] buffer = new byte[1024];
                    int textSize = 0x23af;
                    int fingerprintSize = 0xc;
                    int length;
                    int fLoc = 0;

                    dataInStream.skipBytes(textOffset);
                    fLoc += textOffset;

                    byte[] text = new byte[textSize];
                    if (textSize != dataInStream.read(text)) {
                        Log.d(TAG, ".text load failed");
                    } else {
                        fLoc += textSize;
                    }

                    dataInStream.skipBytes(fingerprintOffset - fLoc);

                    byte[] fingerprint = new byte[fingerprintSize];
                    if (fingerprintSize != dataInStream.read(fingerprint)) {
                        Log.d(TAG, ".fingerprint load failed");
                    }

                    Log.d(TAG, "fingerprint: " + HexData.hexToString(fingerprint));

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

                        /**
                         * Process packet's message content and continue message sequence
                         */
                        if (asciiData.contains("$")) {
                            String messageEncoded = asciiData.split("[$#]")[1];
                            Log.d(TAG, "GDB message received " + messageEncoded);
                            if (messageEncoded.contains("OK")) {
                                sendNextMessage();
                            }
                        }

                        /**
                         * Send ACK if packet fully received.
                         * Not bothering to even check csum or anything...
                         */
                        if (asciiData.contains("#")) {
                            flashService.WriteData(ACK);
                        }

                        timerHandler.postDelayed(this, 100);

                    } else {
                        flashService.StopReadingThread();
                    }
                }
            }

            private void sendNextMessage() {
                if (messageQueue.isEmpty()) {
                    flashService.CloseTheDevice();
                    Log.d(TAG, "flash sequence completed");
                } else {
                    flashService.WriteData(buildPacket(messageQueue.remove()));
                }
            }

            private byte[] buildPacket(String msg) {
                final String startTok = "$";
                final String csumTok = "#";
                StringBuilder packet =  new StringBuilder();

                /**
                 * Calculate checksum
                 */
                Integer csum = 0;
                for (byte b : msg.getBytes()) {
                    csum += b;
                }
                csum %= 256;

                /**
                 * Build packet
                 */
                packet.append(startTok);
                packet.append(msg);
                packet.append(csumTok);
                packet.append(Integer.toHexString(csum));

                return packet.toString().getBytes();
            }

            @Override
            public void onClick(View view) {
                messageQueue.addAll(Arrays.asList(gdbInitSequence));
                /**
                 * Flash the connected NeuroBytes board with correct firmware
                 */
                flashService.OpenDevice();
                flashService.StartReadingThread();
                sendNextMessage();
                Log.d("GDB Received", Boolean.toString(flashService.IsThereAnyReceivedData()));
                GdbCallbackRunnable callback = new GdbCallbackRunnable(flashService);
//                flashService.StartReadingThread();
                timerHandler.postDelayed(callback, 100);
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

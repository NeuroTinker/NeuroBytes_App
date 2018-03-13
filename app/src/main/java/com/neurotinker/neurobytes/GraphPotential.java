package com.neurotinker.neurobytes;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.IItem;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.expandable.ExpandableExtension;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter.listeners.CustomEventHook;
import com.mikepenz.fastadapter.listeners.EventHook;
import com.mikepenz.fastadapter_extensions.drag.ItemTouchCallback;
import com.mikepenz.fastadapter_extensions.drag.SimpleDragCallback;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import io.saeid.fabloading.LoadingView;

import static android.view.View.VISIBLE;

public class GraphPotential extends AppCompatActivity {

    private boolean pingRunning;
    private boolean commsEstablished;

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                   // Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                    break;
                    
                case UsbService.ACTION_NO_USB: // NO USB CONNECTED
                    Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_CDC_DRIVER_NOT_WORKING:
                    Toast.makeText(context, "CDC driver not working", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_DEVICE_NOT_WORKING:
                    Toast.makeText(context, "CDC driver not working", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_READY:
                    Toast.makeText(context, "USB communication established", Toast.LENGTH_SHORT).show();
                    // start sending nid pings
                    commsEstablished = true;
                    if (!pingRunning){
                        Log.d("Message Sent", "NID Ping");
                        timerHandler.postDelayed(pingRunnable, 500);
                        timerHandler.postDelayed(new DelaySendRunnable(makeIdentifyMessage(0)), 3000);
                        pingRunning = true;
                    }
                    break;
            }
        }
    };

    public static final String ACTION_CHANNEL_ACQUIRED = "com.neurotinker.neurobytes.ACTION_CHANNEL_ACQUIRED";

    private final BroadcastReceiver uiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_CHANNEL_ACQUIRED:
                    //TODO: add channel acquisition logic
                    break;
            }
        }
    };

    private static final byte[] blinkMessage = new byte[] {
            (byte) 0b10010000,
            0x0,
            0x0,
            0x0
    };

    private static final byte[] pausePlayMessage = new byte[] {
            (byte) 0b11000000,
            (byte) 0b11000000,
            0x0,
            0x0
    };

    private byte[] makeIdentifyMessage(int ch) {
        //byte b = (byte) ch;
        byte chByte = (byte) ch;
        return new byte[] {
                (byte) 0b11000000,
                (byte) (0b01000000 | (byte) ((byte)(chByte & 0b111) << 3)),
                0x0,
                0x0

        };
    }

    /*
    Data message to a channel.
    4-bit header
    3-bit channel
    5-bit parameter id
    16-bit value
     */

    private byte[] makeDataMessage(int ch, int param, int val) {
        byte chByte = (byte) ch;
        byte paramByte = (byte) param;
        byte valByte1 = (byte) (val & 0xFF);
        byte valByte2 = (byte) ((val >> 8) & 0xFF);
        return new byte[] {
                (byte) (0b11010000 | (byte) chByte << 1),
                (byte) ((paramByte << 4) | ((valByte1 & 0b11110000) >> 4)),
                (byte) (((valByte1 & 0b1111) << 4) | ((valByte2 & 0b11110000) >> 4)),
                (byte) ((valByte2 & 0b1111) << 4)
        };
    }

    private Map<String, Integer> interneuronParams = new HashMap<String, Integer>() {{
        put("current", 0b1);
        put("dendrite1", 0b10);
        put("dendrite2", 0b11);
        put("dendrite3", 0b100);
        put("dendrite4", 0b101);
        put("delay", 0b111);
    }};

    private UsbService usbService;
    public NidHandler nidHandler;

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(nidHandler);
            usbService.write(makeIdentifyMessage(0));
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
            pingRunning = false;
        }
    };

    class SeekBarHook extends CustomEventHook {

        String name;
        View bindView;

        public SeekBarHook(String name, View bindView) {
            this.name = name;
            this.bindView = bindView;
        }

        @Nullable
        @Override
        public View onBind(RecyclerView.ViewHolder viewHolder) {
            if (viewHolder instanceof GraphSubItem.ViewHolder) {
                return ((GraphSubItem.ViewHolder) viewHolder).dendrite4;
            }
            return null;
        }
        @Override
        public void attachEvent(View view, final RecyclerView.ViewHolder viewHolder) {
            ((SeekBar) view).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                int progress;
                int ch = 1;
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    progress = i;
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (expandableExtension.getExpandedItems().length > 0)
                        ch = ((GraphItem) fastAdapter.getItem(expandableExtension.getExpandedItems()[0])).channel;
                    usbService.write(makeDataMessage(ch, interneuronParams.get(name), progress));
                }
            });
        }
    }

    class DelaySendRunnable implements Runnable {
        private byte[] message;
        public DelaySendRunnable(byte[] msg) {
            this.message = msg;
        }
        @Override
        public void run() {
            if (usbService != null)
                usbService.write(this.message);
        }
    }

    Runnable clearRunnable = new Runnable() {
        @Override
        public void run() {
            if (usbService != null) {
                usbService.write(makeIdentifyMessage(0));
            }
        }
    };

    Handler timerHandler = new Handler();
    Runnable pingRunnable = new Runnable() {

        @Override
        public void run() {
            byte[] nidPing = new byte[] {(byte)0b11100000, 0x0, 0x0, 0x0};
            byte[] blink = new byte[] {(byte) 0b10010000, 0x0, 0x0, 0x0};
            int size = graphChannels.size();

            if (usbService != null) {
                usbService.write(nidPing);

                //usbService.write(blink);
            }

            if (usbService != null){
                timerHandler.postDelayed(this, 200);
            } else {
                pingRunning = false;
            }

        }
    };

    Runnable channelUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            for (int i=0; i<itemAdapter.getAdapterItemCount(); i++) {
                if (fastAdapter.getItemViewType(i) == R.id.graphitem_id) {
                    GraphItem item = (GraphItem) fastAdapter.getItem(i);
                    // check channel status ... TODO: put this in a GraphController method
                    if (item.graphController.count > 0) {
                        if (!item.graphController.enabled) {
                            item.graphController.enable();
                            Log.d("Enable channel", Integer.toString(item.channel));
                        }
                    } else if (item.graphController.enabled) {
                        item.graphController.disable();
                    }

                    item.graphController.count = 0;
                    if (((GraphItem) fastAdapter.getItem(i)).isExpanded()){
                        Log.d("test", "t");
                        fastAdapter.notifyAdapterItemChanged(i, GraphItem.UpdateType.CHINFO);
                        expandableExtension.expand(i, false);
                    } else{
                        fastAdapter.notifyAdapterItemChanged(i, GraphItem.UpdateType.CHINFO);
                    }
                }
            }
            timerHandler.postDelayed(channelUpdateRunnable, 1000);
        }
    };

    private UsbFlashService flashService = new UsbFlashService(this, 0x6018, 0x1d50);

    private static final String[] SCOPES = { DriveScopes.DRIVE_METADATA_READONLY, DriveScopes.DRIVE_FILE };
    private GoogleSignInClient mGoogleSignInClient;
    private Bitmap mBitmapToSave;
    private com.google.api.services.drive.Drive driveService = null;

    private int chCnt = 0;
    private Queue<Integer> nextCh = new LinkedList<>();
    private boolean isPaused = false;
    private Map<Integer, GraphController> graphChannels = new HashMap<>();

    private ItemAdapter itemAdapter = new ItemAdapter();
    private FastAdapter fastAdapter = FastAdapter.with(itemAdapter);
    private RecyclerView recyclerView;
    private ExpandableExtension expandableExtension;

    private SimpleDragCallback dragCallback;
    private ItemTouchCallback itemTouchCallback = new ItemTouchCallback() {
        @Override
        public boolean itemTouchOnMove(int oldPosition, int newPosition) {
            Collections.swap(itemAdapter.getAdapterItems(), oldPosition, newPosition); // change position
            fastAdapter.notifyAdapterItemMoved(oldPosition, newPosition);
            return true;
        }

        @Override
        public void itemTouchDropped(int oldPosition, int newPosition) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph_potential);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (usbService != null)
            usbService.write(makeIdentifyMessage(0));
        timerHandler.postDelayed(new DelaySendRunnable(makeIdentifyMessage(0)), 1500);

        expandableExtension = new ExpandableExtension<>();
        expandableExtension.withOnlyOneExpandedItem(true);
        fastAdapter.addExtension(expandableExtension);
        fastAdapter.setHasStableIds(true);

        fastAdapter.withEventHook(new ClickEventHook<GraphItem>() {
            @Nullable
            @Override
            public View onBind(@NonNull RecyclerView.ViewHolder viewHolder) {
                if (viewHolder instanceof GraphItem.ViewHolder) {
                    // bind click event to 'add' icon
                    return viewHolder.itemView.findViewById(R.id.add_id);
                }
                return null;
            }

            @Override
            public void onClick(View v, int position, FastAdapter fastAdapter, GraphItem item) {
                if (usbService != null && commsEstablished) {
                    v.setVisibility(View.GONE);
                    ((View) v.getParent()).findViewById(R.id.loading_id).setVisibility(View.VISIBLE);
                    item.state = GraphItem.GraphState.WAITING;
                    Log.d("Id message sent:", Integer.toString(item.channel));
                    usbService.write(makeIdentifyMessage(item.channel));
                } else {
                    ((View) v.getParent()).findViewById(R.id.nousb_id).setVisibility(View.VISIBLE);
                }
            }
        });

        fastAdapter.withEventHook(new ClickEventHook<GraphItem>() {
            @Nullable
            @Override
            public View onBind(@NonNull RecyclerView.ViewHolder viewHolder) {
                if (viewHolder instanceof GraphItem.ViewHolder) {
                    // bind click event to 'add' icon
                    return viewHolder.itemView.findViewById(R.id.clear_id);
                }
                return null;
            }

            @Override
            public void onClick(View v, int position, FastAdapter fastAdapter, GraphItem item) {
                if (usbService != null) {
                    //itemAdapter.remove(position);
                    nextCh.add(item.channel);
                    usbService.write(makeIdentifyMessage(item.channel));
                    //timerHandler.postDelayed(new DelaySendRunnable(makeIdentifyMessage(chCnt)), 500);
                    graphChannels.remove(item.graphController);
                    itemAdapter.remove(position);
                    //fastAdapter.notifyAdapterDataSetChanged();
                    //fastAdapter.notifyAdapterItemRemoved(position);
                }
            }
        });

        fastAdapter.withEventHook(new CustomEventHook() {
            @Nullable
            @Override
            public View onBind(RecyclerView.ViewHolder viewHolder) {
                if (viewHolder instanceof GraphSubItem.ViewHolder) {
                    return ((GraphSubItem.ViewHolder) viewHolder).dendrite1;
                }
                return null;
            }
            @Override
            public void attachEvent(View view, final RecyclerView.ViewHolder viewHolder) {
                ((SeekBar) view).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    int progress;
                    int ch = 1;
                    GraphItem item;
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                        progress = i;
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        if (expandableExtension.getExpandedItems().length > 0)
                            ch = ((GraphItem) fastAdapter.getItem(expandableExtension.getExpandedItems()[0])).channel;
                        usbService.write(makeDataMessage(ch, interneuronParams.get("dendrite1"), progress));
                    }
                });
            }
        });

        fastAdapter.withEventHook(new CustomEventHook() {
            @Nullable
            @Override
            public View onBind(RecyclerView.ViewHolder viewHolder) {
                if (viewHolder instanceof GraphSubItem.ViewHolder) {
                    return ((GraphSubItem.ViewHolder) viewHolder).dendrite2;
                }
                return null;
            }
            @Override
            public void attachEvent(View view, final RecyclerView.ViewHolder viewHolder) {
                ((SeekBar) view).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    int progress;
                    int ch = 1;
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                        progress = i;
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        if (expandableExtension.getExpandedItems().length > 0)
                            ch = ((GraphItem) fastAdapter.getItem(expandableExtension.getExpandedItems()[0])).channel;
                        usbService.write(makeDataMessage(ch, interneuronParams.get("dendrite2"), progress));
                    }
                });
            }
        });

        fastAdapter.withEventHook(new CustomEventHook() {
            @Nullable
            @Override
            public View onBind(RecyclerView.ViewHolder viewHolder) {
                if (viewHolder instanceof GraphSubItem.ViewHolder) {
                    return ((GraphSubItem.ViewHolder) viewHolder).dendrite3;
                }
                return null;
            }
            @Override
            public void attachEvent(View view, final RecyclerView.ViewHolder viewHolder) {
                ((SeekBar) view).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    int progress;
                    int ch = 1;
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                        progress = i;
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        if (expandableExtension.getExpandedItems().length > 0)
                            ch = ((GraphItem) fastAdapter.getItem(expandableExtension.getExpandedItems()[0])).channel;
                        usbService.write(makeDataMessage(ch, interneuronParams.get("dendrite3"), progress));
                    }
                });
            }
        });

        fastAdapter.withEventHook(new CustomEventHook() {
            @Nullable
            @Override
            public View onBind(RecyclerView.ViewHolder viewHolder) {
                if (viewHolder instanceof GraphSubItem.ViewHolder) {
                    return ((GraphSubItem.ViewHolder) viewHolder).dendrite4;
                }
                return null;
            }
            @Override
            public void attachEvent(View view, final RecyclerView.ViewHolder viewHolder) {
                ((SeekBar) view).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    int progress;
                    int ch = 1;
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                        progress = i;
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        if (expandableExtension.getExpandedItems().length > 0)
                            ch = ((GraphItem) fastAdapter.getItem(expandableExtension.getExpandedItems()[0])).channel;
                        usbService.write(makeDataMessage(ch, interneuronParams.get("dendrite4"), progress));
                    }
                });
            }
        });



        fastAdapter.withEventHook(new CustomEventHook<GraphItem>() {
            @Nullable
            @Override
            public View onBind(RecyclerView.ViewHolder viewHolder) {
                if (viewHolder instanceof GraphItem.ViewHolder) {
                    // bind event to graph view
                    return ((GraphItem.ViewHolder) viewHolder).graphLayout;
                   // return viewHolder.itemView.findViewById(R.id.channel_id);
                }
                return null;
            }
            @Override
            public void attachEvent(final View view, final RecyclerView.ViewHolder viewHolder) {
                view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        int newVis = view.getVisibility();
                        if((int)view.getTag() != newVis)
                        {
                            GraphItem.ViewHolder vHold = (GraphItem.ViewHolder) viewHolder;
                            if (newVis == View.VISIBLE) {
                                ((View) view.getParent()).findViewById(R.id.newcard_id).setVisibility(View.GONE);
                                if (vHold.state == GraphItem.GraphState.NEW) {
                                    vHold.state = GraphItem.GraphState.ACQUIRED;
                                    GraphItem nextItem = new GraphItem(
                                            nextCh.peek() != null ? nextCh.remove() : ++chCnt
                                    );
                                    GraphSubItem nextSubItem = new GraphSubItem();
                                    nextSubItem.withParent(nextItem);
                                    nextItem.withSubItems(Arrays.asList(nextSubItem));
                                    itemAdapter.add(nextItem);
                                    graphChannels.put(nextItem.channel, nextItem.graphController);
                                    //fastAdapter.notifyAdapterDataSetChanged();
                                }
                            }
                            view.setTag(view.getVisibility());
                        }
                    }
                });
            }
        });


        recyclerView = (RecyclerView) this.findViewById(R.id.recview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(fastAdapter);

        dragCallback = new SimpleDragCallback();
        ItemTouchHelper touchHelper = new ItemTouchHelper(dragCallback);
        touchHelper.attachToRecyclerView(recyclerView);

        nidHandler = new NidHandler(this);

        // start the channel management running process
        timerHandler.postDelayed(channelUpdateRunnable, 2000);

        // add the first item to the adapter
        GraphItem firstItem = new GraphItem(++chCnt);
        GraphSubItem firstSubItem = new GraphSubItem();
        firstSubItem.withParent(firstItem);
        firstItem.withSubItems(Arrays.asList(firstSubItem));
        itemAdapter.add(firstItem);
        graphChannels.put(firstItem.channel, firstItem.graphController);
        //usbService.write(makeIdentifyMessage(chCnt));

        ImageView pausePlayView = (ImageView) findViewById(R.id.pauseplay_id);
        pausePlayView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (usbService != null) {
                    usbService.write(pausePlayMessage);
                    ((ImageView) findViewById(R.id.pauseplay_id)).setImageResource(
                            getResources().getIdentifier(
                                    isPaused ? "ic_media_pause" : "ic_media_play",
                                    "drawable",
                                    "android"
                            )
                    );
                    isPaused = !isPaused;
                }
            }
        });

        ImageView recordDataView = (ImageView) findViewById(R.id.record_id);
        recordDataView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    exportData();
                } catch(IOException ie) {
                    ie.printStackTrace();
                }
                //timerHandler.postDelayed(new DelaySendRunnable(makeIdentifyMessage(0)), 1500);
                //fastAdapter.notifyAdapterDataSetChanged();
                //fastAdapter.notifyAdapterItemChanged(0, GraphItem.UpdateType.CHINFO);
            }
        });

        ImageView flashDataView = (ImageView) findViewById(R.id.flash_id);
        flashDataView.setOnClickListener(new View.OnClickListener() {
            class GdbCallbackRunnable implements Runnable {
                private UsbFlashService flashService;
                public GdbCallbackRunnable(UsbFlashService flashService) {
                    this.flashService = flashService;
                }
                @Override
                public void run() {
                    Log.d("GDB Received", Boolean.toString(flashService.IsThereAnyReceivedData()));
                    flashService.CloseTheDevice();
                }
            }
            @Override
            public void onClick(View view) {
                flashService.OpenDevice();
                flashService.StartReadingThread();
                String packet = "$";
                String packetContent = "qRcmd,6D6F6E206C6564";
                byte csum = 0;
                for (byte b : packetContent.getBytes()){
                    csum += b;
                }
                packet += packetContent;
                packet += '#';
                packet += csum;
                flashService.WriteData(packet.getBytes());
                Log.d("GDB Received", Boolean.toString(flashService.IsThereAnyReceivedData()));
                GdbCallbackRunnable callback = new GdbCallbackRunnable(flashService);
                timerHandler.postDelayed(callback, 1000);
                //flashService.CloseTheDevice();
            }
        });
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
        startService(UsbService.class, usbConnection, null); // Start UsbService(if it was not started before) and Bind it
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);
    }

    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        filter.addAction(UsbService.ACTION_CDC_DRIVER_NOT_WORKING);
        filter.addAction(UsbService.ACTION_USB_DEVICE_NOT_WORKING);
        filter.addAction(UsbService.ACTION_USB_READY);
        registerReceiver(mUsbReceiver, filter);
    }

    /*
     * This handler will be passed to UsbService. Data received from serial port is displayed through this handler
     */
    private static class NidHandler extends Handler {
        private final WeakReference<GraphPotential> mActivity;
        public NidHandler(GraphPotential activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    short [] packet = (short []) msg.obj;
                    short headers = packet[0];
                    int channel = (headers & 0b0000011111100000) >> 5;
                    int header =  (headers & 0b1111100000000000) >> 11;
                    short data = packet[1];
                    Log.d("Handling message", Integer.toBinaryString(packet[0]));
                    if (!mActivity.get().graphChannels.isEmpty() && channel>0){
                        if (mActivity.get().graphChannels.get(channel) != null) {
                            Log.d("Channel", Integer.toString(channel));
                            mActivity.get().graphChannels.get(channel).update(data);
                        }
                    } else {
                        Log.d("Message error", "channel out of range" + Integer.toString(channel));
                    }
                    break;
            }
        }
    }
}

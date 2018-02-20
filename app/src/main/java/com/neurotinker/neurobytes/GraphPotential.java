package com.neurotinker.neurobytes;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.IItem;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.expandable.ExpandableExtension;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter.listeners.CustomEventHook;
import com.mikepenz.fastadapter.listeners.EventHook;
import com.mikepenz.fastadapter_extensions.drag.ItemTouchCallback;
import com.mikepenz.fastadapter_extensions.drag.SimpleDragCallback;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.saeid.fabloading.LoadingView;

public class GraphPotential extends AppCompatActivity {

    private boolean pingRunning;

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
                    if (!pingRunning){
                        timerHandler.postDelayed(pingRunnable, 500);
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

    private UsbService usbService;
    private TextView display;
    private EditText editText;
    public NidHandler nidHandler;

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(nidHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
            pingRunning = false;
        }
    };

    Handler timerHandler = new Handler();
    Runnable pingRunnable = new Runnable() {

        @Override
        public void run() {
            byte[] nidPing = new byte[] {(byte)0b11100000, 0x0, 0x0, 0x0};
            byte[] blink = new byte[] {(byte) 0b10010000, 0x0, 0x0, 0x0};
            int size = graphChannels.size();
            for (int i = graphChannels.size()-1; i >= 0; i--) {
                if (graphChannels.get(i).count != 0) {
                    if (!graphChannels.get(i).enabled) {
                        graphChannels.get(i).enabled = true;
                        if (usbService!= null) {
                            usbService.write(makeIdentifyMessage(i+2));
                        }
                        graphChannels.get(i).count = 0;
                    }
                } else {
                    graphChannels.get(i).enabled = false;
                }
            }

            if (usbService != null) {
                usbService.write(nidPing);
                Log.d("Sent message", "NID ping");
                //usbService.write(blink);
            }

            if (usbService != null){
                timerHandler.postDelayed(this, 200);
            } else {
                pingRunning = false;
            }

        }
    };

    private int chCnt = 0;
    private List<GraphController> graphChannels = new ArrayList<GraphController>();

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

        expandableExtension = new ExpandableExtension<>();
        fastAdapter.addExtension(expandableExtension);
        //fastAdapter.withSelectable(true);
        //recyclerView.setItemAnimator(new SlideDownAlphaAnimator());

        fastAdapter.withEventHook(new ClickEventHook() {
            @Nullable
            @Override
            public View onBind(@NonNull RecyclerView.ViewHolder viewHolder) {
                if (viewHolder instanceof GraphItem.ViewHolder) {
                    return viewHolder.itemView;
                }
                return null;
            }
            @Override
            public void onClick(View v, int position, FastAdapter fastAdapter, IItem item) {

            }
        });


        recyclerView = (RecyclerView) this.findViewById(R.id.recview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(fastAdapter);

        dragCallback = new SimpleDragCallback();
        ItemTouchHelper touchHelper = new ItemTouchHelper(dragCallback);
        touchHelper.attachToRecyclerView(recyclerView);

        nidHandler = new NidHandler(this);

        final FloatingActionButton fab1 = (FloatingActionButton) findViewById(R.id.fab1);
        fab1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // add a new graphing channel
                if (graphChannels.size() == 0 || graphChannels.get(graphChannels.size()-1).enabled){
                    GraphItem newItem = new GraphItem(++chCnt);
                    //List<GraphSubItem> subList = new ArrayList<>();
                    //GraphSubItem subItem = new GraphSubItem();
                    //subList.add(subItem);
                    //newItem.withSubItems(subList);
                    itemAdapter.add(newItem);
                    // keep reference to channel for USB comms
                    graphChannels.add(newItem.graphController);
                    usbService.write(makeIdentifyMessage(chCnt));
                }
            }
        });

        final FloatingActionButton fab2 = (FloatingActionButton) findViewById(R.id.fab2);
        fab2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Blink message sent", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                usbService.write(blinkMessage);
            }
        });
    }

    public void addChannel(int ch) {
        // add a new graphing channel
        GraphItem newItem = new GraphItem(ch);
        itemAdapter.add(newItem);
        // keep reference to channel for USB comms
        //graphChannels.add(newItem.graphController);
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
                    if (mActivity.get().graphChannels.size() >= channel){
                        mActivity.get().graphChannels.get(channel-1).update(data);
                    } else {
                        Log.d("Message error", "channel out of range" + Integer.toString(channel));
                    }
                    break;
            }
        }
    }
}

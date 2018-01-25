package com.neurotinker.neurobytes;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;

import java.lang.ref.WeakReference;
import java.util.Set;

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

    private static final byte[] identifyMessage1 = new byte[] {
            (byte) 0b11000000,
            (byte) 0b01001000,
            0x0,
            0x0
    };

    private static final byte[] identifyMessage2 = new byte[] {
            (byte) 0b11000000,
            (byte) 0b01010000,
            0x0,
            0x0
    };
    private static final byte[] identifyMessage3 = new byte[] {
            (byte) 0b11000000,
            (byte) 0b01011000,
            0x0,
            0x0
    };

    private static final byte[] blinkMessage = new byte[] {
            (byte) 0b10010000,
            0x0,
            0x0,
            0x0
    };

    private UsbService usbService;
    private TextView display;
    private EditText editText;
    private NidHandler mHandler;

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler);
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
            int chan1Rate;
            int chan2Rate;
            //Log.d("chan1Rate", Integer.toString(chan1Cnt));
            if (chan1Cnt != 0){
                chan1Rate = chan1Cnt * 10;
                Log.d("channel 1 enabled", Boolean.toString(chan1En));
                if (!chan1En) {
                    chan1En = true;
                    Log.d("channel 1 enabled", Boolean.toString(chan1En));
                    if (usbService != null) {
                        Log.d("Sent message:", "identify 2");
                        usbService.write(identifyMessage2);
                    }
                }
            } else {
                chan1Rate = 0;
                chan1En = false;
            }
            if (chan2Cnt != 0){
                chan2Rate = chan2Cnt * 10;
                if (chan2En != true) {
                    chan2En = true;
                    //Log.d("Sent message:", "identify 3");
                    //usbService.write(identifyMessage3);
                }
            } else {
                chan2Rate = 0;
                chan2En = false;
            }
            if (usbService != null) {
                usbService.write(nidPing);
                Log.d("Sent message", "NID ping");
                //usbService.write(blink);
            }
            chan1Cnt = 0;
            chan2Cnt = 0;
            if (chan1En) Log.d("Channel 1 display rate", Integer.toString(chan1Rate));
            if (chan2En) Log.d("Channel 2 display rate", Integer.toString(chan2Rate));
            if (usbService != null){
                timerHandler.postDelayed(this, 200);
            } else {
                pingRunning = false;
            }

        }
    };

    GraphController graph1;
    GraphController graph2;

    private int chan1Cnt;
    private int chan2Cnt;

    private boolean chan1En;
    private boolean chan2En;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph_potential);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mHandler = new NidHandler(this);

        final FloatingActionButton fab1 = (FloatingActionButton) findViewById(R.id.fab1);
        fab1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("channel 1 enabled", Boolean.toString(chan1En));
                if (chan2En) {
                    usbService.write(identifyMessage2);
                    graph2.clear();
                    Log.d("Sent message:", "identify 2");
                    Snackbar.make(view, "Channel 2 reset", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                } else if (chan1En){
                    graph1.clear();
                    usbService.write(identifyMessage1);
                    Log.d("Sent message:", "identify 1");
                    Snackbar.make(view, "Channel 1 reset", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                } else {
                    usbService.write(identifyMessage1);
                    Log.d("Sent message:", "identify 1");
                    Snackbar.make(view, "All channels clear", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    Log.d("Reset", "USB Communication");
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



            // Initialize Chart

        LineChart chart1 = (LineChart) findViewById(R.id.chart1);
        LineChart chart2 = findViewById(R.id.chart2);

        chart1.setDrawGridBackground(false);

        graph1 = new GraphController();
        graph1.PotentialGraph(chart1);

        chart2.setDrawGridBackground(false);

        graph2 = new GraphController();
        graph2.PotentialGraph(chart2, Color.RED);
        // start sending nid pings
        //timerHandler.postDelayed(pingRunnable, 500);
        //display.append("test"); // debug
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
        private int update1 = 0;
        private boolean update1Ready = false;
        private int update2 = 0;
        private boolean update2Ready = false;
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    short [] packet = (short []) msg.obj;
                    short headers = packet[0];
                    int channel = (headers & 0b0000111111000000) >> 6;
                    short data = packet[1];
                    if (headers == -24544){
                        mActivity.get().chan1Cnt += 1;
                        //update1 = (int) data;
                        //update1Ready = true;
                        mActivity.get().graph1.update(data);
                        //mActivity.get().graph1.addPoint(data);
                        Log.d("Received data", "channel 1");
                    } else if (headers == -24512){
                        mActivity.get().chan2Cnt += 1;
                       // update2 = (int) data;
                       // update2Ready = true;
                        mActivity.get().graph2.update(data);
                        //mActivity.get().graph2.addPoint(data);
                        Log.d("Received data", "channel 2");
                    } else {
                        Log.d("Communication Error", "Packet not understood");
                        Log.d("Malformed Request", Integer.toBinaryString(packet[0]));
                        // invalid packet. this is usually because of tablet and NID going out of sync
                        // so reset the USB connection
                        //mActivity.get().onPause();
                        //mActivity.get().onResume();
                    }
                    /*
                    if (update2Ready && update1Ready){
                        mActivity.get().graph1.addPoint(update1);
                        mActivity.get().graph2.addPoint(update2);
                        update1Ready = false;
                        update2Ready = false;
                    }
                    */
                    //Log.d("Read Channel", Short.toString(headers));
                    break;
            }
        }
    }
}

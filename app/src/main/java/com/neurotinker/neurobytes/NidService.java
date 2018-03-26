package com.neurotinker.neurobytes;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.sql.DataTruncation;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Created by jrwhi on 3/24/2018.
 */

public class NidService extends Service {

    private static final String TAG = NidService.class.getSimpleName();

    public static final String ACTION_RECEIVED_DATA = "com.neurotinker.neurobytes.ACTION_RECEIVED_DATA";
    public static final String ACTION_NID_DISCONNECTED = "com.neurotinker.neurobytes.ACTION_NID_DISCONNECTED";
    public static final String ACTION_NID_CONNECTED = "com.neurotinker.neurobytes.ACTION_NID_CONNECTED";
    public static final String ACTION_NID_READY = "com.neurotinker.neurobytes.ACTION_NID_READY";
    public static final String ACTION_SEND_BLINK = "com.neurotinker.neurobytes.ACTION_SEND_BLINK";
    public static final String ACTION_ADD_CHANNEL = "com.neurotinker.neurobytes.ACTION_SEND_IDENTIFY";
    public static final String ACTION_REMOVE_CHANNEL = "com.neurotinker.neurobytes.ACTION_REMOVE_CHANNEL";

    public static final String DATA = "com.neurotinker.neurobytes.DATA";
    public static final String CHANNEL = "com.neurotinker.neurobytes.CHANNEL";

    /**
     * NidService States
     *
     * NOT_CONNECTED - not connected to UsbService. no ping messages being sent
     * WAITING - connected to UsbService, waiting for initialization
     * STOPPED - halted by UsbService. no ping messages being sent
     * RUNNING - connected to UsbService, ping messages being sent and okay to send commands
     * CORRECTING - communication fault in UsbService, trying to fix it, ping messages still fine
     *
     * In STOPPED state, no ping message sent in past 200 ms so the network has forgotten NID.
     * In CORRECTING state, a ping message has been sent in the past 200 ms.
     */
    public enum State {
        NOT_CONNECTED,
        WAITING,
        STOPPED,
        CORRECTING,
        RUNNING,
        QUITTING
    }

    private UsbService usbService;
    private UsbCallback usbCallback;
    public static NidService.State state;
    private Context context;
    private IBinder binder = new NidBinder();

    @Override
    public void onCreate() {
        this.context = this;
        this.state = State.NOT_CONNECTED;
        usbCallback = new UsbCallback();
    }

    /**
     * Can bind to individual channels or to main activity
     *
     * @param intent
     * @return
     */
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setFilters();
        startService(UsbService.class, usbConnection, null);
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        state = State.QUITTING;
        unregisterReceiver(usbReceiver);
        unregisterReceiver(commandReceiver);
        unbindService(usbConnection);
    }

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            usbService = ((UsbService.UsbBinder) iBinder).getService();
            usbService.setHandler(new Handler(usbCallback));
            state = NidService.State.WAITING;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            usbService = null;
            state = NidService.State.NOT_CONNECTED;
        }
    };

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

    private class UsbCallback implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    short [] packet = (short []) msg.obj;
                    NbMessage nbMsg = new NbMessage(packet);
                    if (nbMsg.isValid){
                        Intent intent = new Intent(ACTION_RECEIVED_DATA);
                        intent.putExtra(CHANNEL, nbMsg.channel);
                        intent.putExtra(DATA, nbMsg.data);
                        sendBroadcast(intent);
                    } else {
                        Log.d(TAG, "Received invalid message");
                    }
                    break;
            }
            return true;
        }
    }

    private class NidBinder extends Binder {
        public NidService getService() {
            return NidService.this;
        }
    }

    /**
     * MessageScheduler
     *
     * schedules sending timed messages (e.g. pingMessage)
     * schedules one-off packets (e.g. blinkMessage)
     */

    /**
     * sendPing is ran through a ping handler every 200 ms
     */
    public void sendPing() {

    }

    public void sendMessage(NidMessage msg) {
        if (usbService != null && state == State.RUNNING) {
            usbService.write(PING_MESSAGE);
        }
    }

    private final BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_SEND_BLINK:
                    usbService.write(BLINK_MESSAGE);
                    break;
                case ACTION_ADD_CHANNEL:
                    /**
                     * Send identify X command
                     * Re-send identify X command until channel has been identified
                     * Once identified, Send CHANNEL_ACQUIRED broadcast with board info
                     */
                    break;
            }
        }
    };

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    break;
                case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    Toast.makeText(context, "USB Permission not granted",
                            Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_NO_USB: // NO USB CONNECTED

                    break;
                case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();
                    state = State.NOT_CONNECTED;
                    break;
                case UsbService.ACTION_USB_READY:
                    Toast.makeText(context, "USB communication established",
                            Toast.LENGTH_SHORT).show();
                    // start sending nid pings
                    commsEstablished = true;
                    if (!pingRunning){
                        Log.d("Message Sent", "NID Ping");
                        timerHandler.postDelayed(pingRunnable, 500);
                        timerHandler.postDelayed(new MainActivity.DelaySendRunnable(
                                makeIdentifyMessage(0)), 3000);
                        pingRunning = true;
                    }
                    break;
            }
        }
    };

    private void setFilters() {
        IntentFilter filter = new IntentFilter();

        filter.addAction(NidService.ACTION_ADD_CHANNEL);
        filter.addAction(NidService.ACTION_REMOVE_CHANNEL);
        filter.addAction(NidService.ACTION_SEND_BLINK);

        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        filter.addAction(UsbService.ACTION_CDC_DRIVER_NOT_WORKING);
        filter.addAction(UsbService.ACTION_USB_DEVICE_NOT_WORKING);
        filter.addAction(UsbService.ACTION_USB_READY);

        registerReceiver(usbReceiver, filter);
        registerReceiver(commandReceiver, filter);
    }
}

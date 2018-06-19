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
import android.os.Looper;
import android.os.Message;
import android.support.annotation.RestrictTo;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.sql.DataTruncation;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
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
    public static final String ACTION_CHANNEL_ACQUIRED = "com.neurotinker.neurobytes.ACTION_CHANNEL_ACQUIRED";
    public static final String ACTION_REMOVE_CHANNEL = "com.neurotinker.neurobytes.ACTION_REMOVE_CHANNEL";
    public static final String ACTION_SEND_PAUSE = "com.neurotinker.neurobytes.ACTION_SEND_PAUSE";
    public static final String ACTION_SEND_DATA = "com.neurotinker.neurobytes.ACTION_SEND_DATA";
    public static final String ACTION_PAUSE_COMMS = "com.neurotinker.neurobytes.ACTION_PAUSE_COMMS";
    public static final String ACTION_RESUME_COMMS = "com.neurotinker.neurobytes.ACTION_RESUME_COMMS";

    public static final String BUNDLE_DATA_POTENTIAL = "com.neurotinker.neurobytes.BUNDLE_DATA_POTENTIAL";
    public static final String BUNDLE_DATA_TYPE = "com.neurotinker.neurobytes.BUNDLE_DATA_TYPE";
    public static final String BUNDLE_DATA_WEIGHTING = "com.neurotinker.neurobytes.BUNDLE_DATA_WEIGHTING";
    public static final String BUNDLE_DATA_VALUE = "com.neurotinker.neurobytes.BUNDLE_DATA_VALUE";
    public static final String BUNDLE_DATA_PARAM = "com.neurotinker.neurobytes.BUNDLE_DATA_PARAM";
    public static final String BUNDLE_CHANNEL_TYPE = "com.neurotinker.neurobytes.BUNDLE_CHANNEL_TYPE";
    public static final String BUNDLE_CHANNEL = "com.neurotinker.neurobytes.BUNDLE_CHANNEL";

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

    private final IBinder binder = new NidBinder();

    @RestrictTo(RestrictTo.Scope.TESTS)
    public static boolean isStarted;

    private UsbService usbService;
    private UsbCallback usbCallback;
    public State state;
    private Context context;
    SendMessageRunnable pingRunnable;
    SendMessageRunnable identifyRunnable;
    boolean isIdentifying;
    int identifyingChannel;

    @Override
    public void onCreate() {
        this.context = this;
        Log.d(TAG, "NidService.onCreate");
        super.onCreate();
        isStarted = true;
        this.state = State.NOT_CONNECTED;
        usbCallback = new UsbCallback();

//        registerReceiver(new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//
//            }
//        }, new IntentFilter());

        setFilters();
        Log.d(TAG, "NidService started");

        startService(UsbService.class, usbConnection, null);
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

        Log.d(TAG, "NidService onStartCommand");
        //context = getApplicationContext();
        return Service.START_STICKY;
    }

    public static boolean isServiceStarted() {
        return isStarted;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        state = State.QUITTING;
        if (pingRunnable != null)
            pingRunnable.stop();
        unregisterReceiver(usbReceiver);
        unregisterReceiver(commandReceiver);
        unbindService(usbConnection);
    }

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            usbService = ((UsbService.UsbBinder) iBinder).getService();
            usbService.setHandler(new Handler(usbCallback));
            Log.d(TAG, "UsbService connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            usbService = null;
            state = State.NOT_CONNECTED;
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
                    Log.d(TAG, String.format("received %s", Integer.toBinaryString(packet[0])));

                    if (nbMsg.isValid){
                        Intent intent = new Intent(ACTION_RECEIVED_DATA);
                        intent.putExtra(BUNDLE_CHANNEL, nbMsg.channel);
                        if (nbMsg.checkSubheader(NbMessage.Subheader.POTENTIAL)) {
                            intent.putExtra(BUNDLE_DATA_POTENTIAL, nbMsg.data);
                            sendBroadcast(intent);
                        } else if (nbMsg.checkSubheader(NbMessage.Subheader.TYPE)) {
                            Log.d(TAG, "received type message");
                            intent.putExtra(BUNDLE_DATA_TYPE, nbMsg.data);
                            sendBroadcast(intent);
                            if (isIdentifying) {
                                // new channel has been acquired
                                sendBroadcast(new Intent(ACTION_CHANNEL_ACQUIRED));
                                identifyRunnable.stop();
                                sendMessage(STOP_IDENTIFY);
                                isIdentifying = false;
                            } else {
                                Log.d(TAG, "Duplicate channel acquired");
                            }
                        }
                    } else {
                        Log.d(TAG, String.format("Received invalid message %s",
                                Integer.toBinaryString(nbMsg.header)));
                        UsbService.correctFlag = true;
                    }
                    break;
            }
            return true;
        }
    }

    public class NidBinder extends Binder {
        NidService getService() {
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
        if (usbService != null && state == State.RUNNING) {
            usbService.write(PING_MESSAGE);
        }
    }

    public void sendMessage(byte[] msg) {
        if (usbService != null && state == State.RUNNING) {
            usbService.write(msg);
        }
    }

    private final BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int ch = intent.getIntExtra(BUNDLE_CHANNEL, 0);
            int type = intent.getIntExtra(BUNDLE_CHANNEL_TYPE, 0);
            int param = intent.getIntExtra(BUNDLE_DATA_PARAM, 0);
            int val = intent.getIntExtra(BUNDLE_DATA_VALUE, 0);
            switch (intent.getAction()) {
                case ACTION_SEND_BLINK:
                    Log.d(TAG, "sending blink");
                    sendMessage(BLINK_MESSAGE);
                    break;
                case ACTION_ADD_CHANNEL:
                    /**
                     * Send identify X command
                     * Re-send identify X command until channel has been identified
                     * Once identified, Send CHANNEL_ACQUIRED broadcast with board info
                     * Finish by sending ID_DONE message to network so no more boards ID
                     */
                    Log.d(TAG, "sending identify");
                    identifyRunnable = new SendMessageRunnable(makeIdentifyMessage(ch), 500);
                    timerHandler.postDelayed(identifyRunnable, 100);
                    isIdentifying = true;
                    break;
                case ACTION_SEND_DATA:
                    Log.d(TAG, String.format("sending data"));
                    sendMessage(makeDataMessage(
                            ch,
                            param,
                            val
                    ));
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
                    Log.d(TAG, "USB_READY");
                    Toast.makeText(context, "USB communication established",
                            Toast.LENGTH_SHORT).show();

                    /**
                     * Start initialization sequence:
                     * 10 ms - start sending pings
                     * 1000 ms - send clear channel command
                     * 2000 ms - enable NID communications
                     */

                    if (state == State.NOT_CONNECTED) {
                        pingRunnable = new SendMessageRunnable(PING_MESSAGE, 200);
                        timerHandler.postDelayed(pingRunnable, 10);
                        timerHandler.postDelayed(new SendMessageRunnable(CLEAR_CHANNEL), 1000);
                        timerHandler.postDelayed(new ChangeStateRunnable(State.RUNNING), 2000);
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
        filter.addAction(NidService.ACTION_NID_DISCONNECTED);
        filter.addAction(NidService.ACTION_NID_CONNECTED);
        filter.addAction(NidService.ACTION_SEND_DATA);

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

    Handler timerHandler = new Handler(Looper.getMainLooper());
    class SendMessageRunnable implements Runnable {
        private byte[] message;
        boolean isRepeating;
        int repeatTime;

        public SendMessageRunnable(byte[] message) {
            this.message = message;
        }

        public SendMessageRunnable(byte[] message, int repeatTime) {
            this.message = message;
            this.isRepeating = true;
            this.repeatTime = repeatTime;
        }

        @Override
        public void run() {
            sendMessage(message);
            if (isRepeating) timerHandler.postDelayed(this, repeatTime);
        }

        public void stop() {
            this.isRepeating = false;
        }
    }

    class ChangeStateRunnable implements Runnable {
        private State newState;

        public ChangeStateRunnable(State newState) {
            this.newState = newState;
        }

        @Override
        public void run() {
            state = newState;
            if (newState == State.RUNNING) {
                Log.d(TAG, "NID running");
                sendMessage(BLINK_MESSAGE);
                Intent intent = new Intent(ACTION_NID_READY);
                sendBroadcast(intent);
            }
        }
    }

    /**
     * Temporarily use these command builders instead of NidMessage
     * TODO: Use NidMessage to build messages
     */

    private final byte[] PING_MESSAGE = new byte[] {
            (byte)0b11100000, 0x0, 0x0, 0x0
    };

    private final byte[] BLINK_MESSAGE = new byte[]{
            (byte) 0b10010000,
            0x0,
            0x0,
            0x0
    };

    private final byte[] pausePlayMessage = new byte[]{
            (byte) 0b11000000,
            (byte) 0b11000000,
            0x0,
            0x0
    };

    private byte[] makeIdentifyMessage(int ch) {
        //byte b = (byte) ch;
        byte chByte = (byte) ch;
        return new byte[]{
                (byte) 0b11000000,
                (byte) (0b01000000 | (byte) ((byte) (chByte & 0b111) << 3)),
                0x0,
                0x0

        };
    }

    private final byte[] CLEAR_CHANNEL = makeIdentifyMessage(0);
    private final byte[] STOP_IDENTIFY = makeIdentifyMessage(7);

    /*
    Data message to a channel.
    4-bit header
    3-bit channel
    5-bit parameter id
    16-bit value
     */

    public byte[] makeDataMessage(int ch, int param, int val) {
        byte chByte = (byte) ch;
        byte paramByte = (byte) param;
        byte valByte1 = (byte) (val & 0xFF);
        byte valByte2 = (byte) ((val >> 8) & 0xFF);
        return new byte[]{
                (byte) (0b11010000 | (byte) chByte << 1),
                (byte) ((paramByte << 4) | ((valByte1 & 0b11110000) >> 4)),
                (byte) (((valByte1 & 0b1111) << 4) | ((valByte2 & 0b11110000) >> 4)),
                (byte) ((valByte2 & 0b1111) << 4)
        };
    }

    private final Map<String, Integer> interneuronParams = new HashMap<String, Integer>() {{
        put("current", 0b1);
        put("dendrite1", 0b10);
        put("dendrite2", 0b11);
        put("dendrite3", 0b100);
        put("dendrite4", 0b101);
        put("delay", 0b111);
    }};
}

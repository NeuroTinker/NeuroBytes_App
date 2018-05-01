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
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by jrwhi on 3/24/2018.
 */

public class DummyNidService extends Service {

    private static final String TAG = DummyNidService.class.getSimpleName();

    public static final String ACTION_RECEIVED_DATA = "com.neurotinker.neurobytes.ACTION_RECEIVED_DATA";
    public static final String ACTION_NID_DISCONNECTED = "com.neurotinker.neurobytes.ACTION_NID_DISCONNECTED";
    public static final String ACTION_NID_CONNECTED = "com.neurotinker.neurobytes.ACTION_NID_CONNECTED";
    public static final String ACTION_NID_READY = "com.neurotinker.neurobytes.ACTION_NID_READY";
    public static final String ACTION_SEND_BLINK = "com.neurotinker.neurobytes.ACTION_SEND_BLINK";
    public static final String ACTION_ADD_CHANNEL = "com.neurotinker.neurobytes.ACTION_SEND_IDENTIFY";
    public static final String ACTION_CHANNEL_ACQUIRED = "com.neurotinker.neurobytes.ACTION_CHANNEL_ACQUIRED";
    public static final String ACTION_REMOVE_CHANNEL = "com.neurotinker.neurobytes.ACTION_REMOVE_CHANNEL";

    public static final String BUNDLE_DATA_POTENTIAL = "com.neurotinker.neurobytes.BUNDLE_DATA_POTENTIAL";
    public static final String BUNDLE_DATA_TYPE = "com.neurotinker.neurobytes.BUNDLE_DATA_TYPE";
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

    private final Map<Integer, ReceiveDataRunnable> channels = new HashMap<Integer, ReceiveDataRunnable>();

//    @RestrictTo(RestrictTo.Scope.TESTS)
    public static boolean isStarted;

    public DummyNidService.State state;
    private Context context;
    boolean isIdentifying;
    int identifyingChannel;

    @Override
    public void onCreate() {
        context = this;
        Log.d(TAG, "NidService.onCreate");
        super.onCreate();
        isStarted = true;
        this.state = State.RUNNING;

        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

            }
        }, new IntentFilter());


        setFilters();
        Log.d(TAG, "NidService started");
    }

    /**
     * Can bind to individual channels or to main activity
     *
     * @param intent
     * @return
     */
    @Override
    public IBinder onBind(Intent intent) {
        throw null;
      // return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        return Service.START_NOT_STICKY;

        Log.d(TAG, "NidService started");
        //context = getApplicationContext();
        return Service.START_STICKY;
    }

    public static boolean isServiceStarted() {
        return isStarted;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
//        state = State.QUITTING;
//        pingRunnable.stop();
//        unregisterReceiver(usbReceiver);
//        unregisterReceiver(commandReceiver);
//        unbindService(usbConnection);
    }

    public class NidBinder extends Binder {
        DummyNidService getService() {
            return DummyNidService.this;
        }
    }

    private final BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_SEND_BLINK:
                    break;
                case ACTION_ADD_CHANNEL:
                    /**
                     * Send identify X command
                     * Re-send identify X command until channel has been identified
                     * Once identified, Send CHANNEL_ACQUIRED broadcast with board info
                     * Finish by sending ID_DONE message to network so no more boards ID
                     */
                    int ch = intent.getIntExtra(BUNDLE_CHANNEL, 0);
                    ReceiveDataRunnable newCh = new ReceiveDataRunnable(ch);
                    channels.put(ch, newCh);
                    timerHandler.postDelayed(newCh, 1000);
                    break;
            }
        }
    };

    private void setFilters() {
        IntentFilter filter = new IntentFilter();

        filter.addAction(DummyNidService.ACTION_ADD_CHANNEL);
        filter.addAction(DummyNidService.ACTION_REMOVE_CHANNEL);
        filter.addAction(DummyNidService.ACTION_SEND_BLINK);
        filter.addAction(DummyNidService.ACTION_NID_DISCONNECTED);
        filter.addAction(DummyNidService.ACTION_NID_CONNECTED);
        context.registerReceiver(commandReceiver, filter);
        context.getApplicationContext().registerReceiver(commandReceiver, filter);
    }

    Handler timerHandler = new Handler(Looper.getMainLooper());

    class ChangeStateRunnable implements Runnable {
        private State newState;

        public ChangeStateRunnable(State newState) {
            this.newState = newState;
        }

        @Override
        public void run() {
            state = newState;
            if (newState == State.RUNNING) {
                Intent intent = new Intent(ACTION_NID_READY);
                sendBroadcast(intent);
            }
        }
    }

    class ReceiveDataRunnable implements Runnable {
        private int ch;
        private int step = 0;
        private int pot = 0;
        private boolean isRunning = true;

        public ReceiveDataRunnable(int ch) {this.ch = ch;}

        private int nextPotential() {
            if (step == 100 || pot == 200) {
                pot += 8000;
            } else if (pot == 300) {
                step = 0;
            }
            if (pot > 10000) {
                pot = -6000;
            }

            pot *= 63/64;

            return pot;
        }

        @Override
        public void run() {
            Intent intent = new Intent(ACTION_RECEIVED_DATA);
            intent.putExtra(BUNDLE_DATA_POTENTIAL, nextPotential());
            intent.putExtra(BUNDLE_CHANNEL, ch);
            sendBroadcast(intent);
            timerHandler.postDelayed(this, 50);
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

package com.neurotinker.neurobytes;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;

/**
 * Created by jarod on 3/23/18.
 */

public class NidController {
    private static final String TAG = NidController.class.getSimpleName();

    /**
     * NidController States
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
    public static enum State {
        NOT_CONNECTED,
        WAITING,
        STOPPED,
        CORRECTING,
        RUNNING
    }
    private State state = State.NOT_CONNECTED;

    private static Context context;

    /**
     * Connect to data receivers and UsbService
     */

    private UsbService usbService;
    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            usbService = ((UsbService.UsbBinder) iBinder).getService();
            state = State.WAITING;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            usbService = null;
            state = State.NOT_CONNECTED;
        }
    };

    /**
     * UsbHandler is called by UsbService when a NID packet has been received
     */
    private static class UsbHandler extends Handler {
        private final WeakReference<MainActivity> mainActivity;
        public UsbHandler(MainActivity activity) { mainActivity = new WeakReference<>(activity); }

        @Override
        public void handleMessage (Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    short [] packet = (short []) msg.obj;
                    short headers = packet[0];
                    int channel = (headers & 0b0000011111100000) >> 5;
                    int header =  (headers & 0b1111100000000000) >> 11;
                    short data = packet[1];
                    break;
            }
        }
    }

    /**
     * MessageScheduler
     *
     * schedules sending timed messages (e.g. pingMessage)
     * schedules one-off packets (e.g. blinkMessage)
     */

    public void sendPingMessage () {

    }

}

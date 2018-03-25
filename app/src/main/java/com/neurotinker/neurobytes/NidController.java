package com.neurotinker.neurobytes;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

/**
 * Created by jarod on 3/23/18.
 */

public class NidController {
    private static final String TAG = NidController.class.getSimpleName();

    public static enum State {
        NOT_CONNECTED,
        WAITING,
        STOPPED,
        CORRECTING,
        RUNNING
    }
    private State state = State.NOT_CONNECTED;


    public void NidController()

    private UsbService usbService;
    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            usbService = ((UsbService.UsbBinder) iBinder).getService();
            usbService.setHandler(usbHandler);
            state = State.WAITING;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            usbService = null;
            state = State.NOT_CONNECTED;
        }
    };

    private static class UsbHandler extends Handler {
        @Override
        public void handleMessage (Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    short [] packet = (short []) msg.obj;
                    short headers = packet[0];
                    int channel = (headers & 0b0000011111100000) >> 5;
                    int header =  (headers & 0b1111100000000000) >> 11;
                    short data = packet[1];
                    if (!mActivity.get().graphChannels.isEmpty() && channel>0){
                        if (mActivity.get().graphChannels.get(channel) != null) {
                            Log.d("Channel", Integer.toString(channel));
                            mActivity.get().graphChannels.get(channel).update(data);
                        }
                    } else {
                        Log.e(TAG, "Channel out of range: " + Integer.toString(channel));
                    }
                    break;
            }
        }
    }

    public void sendPingMessage () {

    }

}

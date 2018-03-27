package com.neurotinker.neurobytes;

import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Created by jarod on 3/27/18.
 */

public class ChannelController {
    private static final String TAG = "Channel Controller";
    private FrameLayout view;

    public enum State {
        ADD,
        WAITING,
        ACQUIRED,
        LOST;
    }

    private State state;

    public ChannelController(FrameLayout view) {
        this.view = view;
        this.state = State.ADD;
    }

    public void setAcquired() {
        if (state == State.WAITING) {
            view.findViewById(R.id.channel_id).setVisibility(View.VISIBLE);
            this.state = State.ACQUIRED;
            view.findViewById(R.id.newcard_id).setVisibility(View.GONE);
        } else {
            Log.e(TAG, "Channel already acquired");
        }
    }
}

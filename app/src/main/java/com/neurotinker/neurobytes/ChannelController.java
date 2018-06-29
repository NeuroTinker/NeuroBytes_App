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
    public Integer firingRate;
    public Integer count;
    private GraphView graphView;

    public enum State {
        ADD,
        WAITING,
        ACQUIRED,
        LOST;
    }

    private State state;

    public ChannelController() {
        this.state = State.ADD;
        this.firingRate = 0;
        this.count = 1;
    }

    public void setLayout(FrameLayout layout) {
        this.view = layout;
    }

    public void setGraph(GraphView graphView) {
        this.graphView = graphView;
    }

    public void update(int data) {
        graphView.update(data);
    }

    public void resume() {
        graphView.resume();
    }

    public void pause() {
        graphView.pause();
    }

    public void disable() {
        this.state = State.ADD;
        view.findViewById(R.id.channel_id).setVisibility(View.GONE);
        view.findViewById(R.id.newcard_id).setVisibility(View.VISIBLE);
    }

    public void setAcquired() {
        view.findViewById(R.id.channel_id).setVisibility(View.VISIBLE);
        this.state = State.ACQUIRED;
        view.findViewById(R.id.newcard_id).setVisibility(View.GONE);
    }
}

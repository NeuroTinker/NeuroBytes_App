package com.neurotinker.neurobytes;

import android.graphics.Color;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.saeid.fabloading.LoadingView;

/**
 * Created by jrwhi on 2/15/2018.
 *
 * Defines individual graph item.
 * Has four states:
 * 1. Plus button. Waiting for user to add channel.
 * 2. Loading Icon. Waiting for channel acquisition.
 * 3. Graphing. Communication established with device.
 * 4. Stalled. Communication lost and attempting to reconnect.
 *
 * init ->
 *          [State 1] ->
 *                         click event ->
 *                                          [State 2] ->
 *
 * [State 2] ->
 *              USB comms acquired ->
 *                                    [State 3]
 *
 *
 *
 * Interfaces:
 * 1. Constructor [self] (channel num)
 *
 */

// AbstractItem<GraphItem, GraphItem.ViewHolder>
public class AddGraphItem extends AbstractItem<AddGraphItem, AddGraphItem.ViewHolder> {
    public String name;
    public int channel;
    public GraphController graphController;

    public AddGraphItem(int ch) {
        this.channel = ch;
        this.name = "Channel " + ch;
        this.graphController = new GraphController();
    }

    @Override
    public int getType() {
        return R.id.addgraphitem_id;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.add_graph_item;
    }

    @Override
    public ViewHolder getViewHolder(@NonNull View v) {
        return new ViewHolder(v);
    }

    protected static class ViewHolder extends FastAdapter.ViewHolder<GraphItem> {
        @BindView(R.id.name)
        TextView name;

        @BindView(R.id.chart)
        LineChart chart;

        @BindView(R.id.loading_view)
        LoadingView loadingView;

        @BindView(R.id.shine)
        ImageView shine;

        public ViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }

        @Override
        public void bindView(GraphItem item, List<Object> payloads) {
            name.setText(item.name);

            // initialize chart
            chart.setDrawGridBackground(false);
            item.graphController.PotentialGraph(chart);

            // start loading animation
            loadingView.addAnimation(Color.BLUE, R.drawable.photoreceptor_square, LoadingView.FROM_BOTTOM);
            loadingView.addAnimation(Color.RED, R.drawable.tonic_square, LoadingView.FROM_RIGHT);
            loadingView.addAnimation(Color.CYAN, R.drawable.touch_square, LoadingView.FROM_TOP);
            loadingView.addAnimation(Color.MAGENTA, R.drawable.interneuron_square, LoadingView.FROM_LEFT);
            loadingView.startAnimation();

            // test shine animation
            Animation animation = new TranslateAnimation(0, 250,100, 0);
            animation.setDuration(500);
            animation.setFillAfter(false);
            animation.setInterpolator(new AccelerateDecelerateInterpolator());
            shine.startAnimation(animation);
        }

        @Override
        public void unbindView(GraphItem item) {
            name.setText(null);
        }
    }
}

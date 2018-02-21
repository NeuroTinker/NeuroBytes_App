package com.neurotinker.neurobytes;


import android.graphics.Color;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.Shape;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.saeid.fabloading.LoadingView;

/**
 * Created by jarod on 2/6/18.
 */

// AbstractItem<GraphItem, GraphItem.ViewHolder>
public class GraphItem extends AbstractExpandableItem<GraphItem, GraphItem.ViewHolder, GraphSubItem>{
    public String name;
    public int channel;
    public GraphController graphController;

    public enum GraphState {
        NEW,
        WAITING,
        ACQUIRED,
        STALLED,
        CLOSING
    }

    public GraphState state;

    public GraphItem(int ch) {
        this.channel = ch;
        this.name = "Channel " + ch;
        this.graphController = new GraphController();
        this.state = GraphState.NEW;
    }

    @Override
    public int getType() {
        return R.id.graphitem_id;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.graph_item;
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

        @BindView(R.id.add_id)
        ImageView add;

        @BindView(R.id.loading_id)
        ProgressBar loading;

        @BindView(R.id.newcard_id)
        RelativeLayout newcard;

        @BindView(R.id.channel_id)
        RelativeLayout graphLayout;

        GraphState state;

        public ViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }

        @Override
        public void bindView(GraphItem item, List<Object> payloads) {
            name.setText(item.name);
            state = GraphState.NEW;

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

            // set listener for change in chart visibility
            graphLayout.setTag(graphLayout.getVisibility());
        }

        @Override
        public void unbindView(GraphItem item) {
            newcard.setVisibility(View.VISIBLE);
            loading.setVisibility(View.GONE);
            add.setVisibility(View.VISIBLE);
            graphLayout.setVisibility(View.GONE);
            state = GraphState.NEW;
        }
    }
}

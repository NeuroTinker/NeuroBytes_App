package com.neurotinker.neurobytes;


import android.graphics.Color;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.IItem;
import com.mikepenz.fastadapter.ISubItem;
import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.materialize.holder.StringHolder;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.saeid.fabloading.LoadingView;

/**
 * Created by jarod on 2/6/18.
 */

// AbstractItem<GraphItem, GraphItem.ViewHolder>
public class AddItem extends AbstractItem<AddItem, AddItem.ViewHolder> {
    public int channel;
    public ChannelController channelController;

    public enum GraphState {
        NEW,
        WAITING,
        ACQUIRED,
        STALLED,
        CLOSING
    }

    public enum UpdateType {
        CHINFO
    }

    public GraphState state;

    public AddItem(int ch) {
        this.channel = ch;
        this.state = GraphState.NEW;
        this.channelController = new ChannelController(ch);
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

    protected static class ViewHolder extends FastAdapter.ViewHolder<AddItem> {
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

        @BindView(R.id.firingrate_id)
        TextView firingRate;

        @BindView(R.id.boardtype_id)
        ImageView boardType;

        @BindView(R.id.channelname_id)
        TextView channelName;

        @BindView(R.id.graphitem_id)
        FrameLayout channelLayout;

        GraphState state;

        StringHolder rateHolder;

        public ViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }

        @Override
        public void bindView(AddItem item, List<Object> payloads) {

            /**
             * Upon binding:
             * Initialize channel controller
             * Initialize chart. Board-specific panel info and submenu stuff gets initialized later
             */

            if (payloads.isEmpty()) {
                Log.d("bind w/ expanded", Boolean.toString(item.isExpanded()));

                // initialize channelController
                item.channelController = new ChannelController(channelLayout);

                // start loading animation
                loadingView.addAnimation(Color.BLUE, R.drawable.photoreceptor_square, LoadingView.FROM_BOTTOM);
                loadingView.addAnimation(Color.RED, R.drawable.tonic_square, LoadingView.FROM_RIGHT);
                loadingView.addAnimation(Color.CYAN, R.drawable.touch_square, LoadingView.FROM_TOP);
                loadingView.addAnimation(Color.MAGENTA, R.drawable.interneuron_square, LoadingView.FROM_LEFT);
                loadingView.startAnimation();

                // test shine animation
                Animation animation = new TranslateAnimation(0, 250, 100, 0);
                animation.setDuration(500);
                animation.setFillAfter(false);
                animation.setInterpolator(new AccelerateDecelerateInterpolator());
                shine.startAnimation(animation);

                // set listener for change in chart visibility
                graphLayout.setTag(graphLayout.getVisibility());
            } else if (payloads.contains(UpdateType.CHINFO)) {
                Log.d("bind just", "chinfo");
                Log.d("bind w/ expanded", Boolean.toString(item.isExpanded()));
            }
        }

        @Override
        public void unbindView(AddItem item) {
            Log.d("unbind channel", Integer.toString(item.channel));
            newcard.setVisibility(View.VISIBLE);
            loading.setVisibility(View.GONE);
            add.setVisibility(View.VISIBLE);
            graphLayout.setVisibility(View.GONE);
            state = GraphState.NEW;
        }
    }
}

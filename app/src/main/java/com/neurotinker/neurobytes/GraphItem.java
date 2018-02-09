package com.neurotinker.neurobytes;


import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.materialize.holder.StringHolder;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by jarod on 2/6/18.
 */

// AbstractItem<GraphItem, GraphItem.ViewHolder>
public class GraphItem extends AbstractExpandableItem<GraphItem, GraphItem.ViewHolder, GraphSubItem>{
    public String name;
    public int channel;
    public GraphController graphController;

    public GraphItem(int ch) {
        this.channel = ch;
        this.name = "Channel " + ch;
        this.graphController = new GraphController();
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
        }

        @Override
        public void unbindView(GraphItem item) {
            name.setText(null);
        }
    }
}

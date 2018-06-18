package com.neurotinker.neurobytes;

import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.ISubItem;
import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.materialize.holder.StringHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by jarod on 2/9/18.
 */

public class GraphSubItem<Parent extends ISubItem> extends AbstractExpandableItem<GraphItem, GraphSubItem.ViewHolder, GraphSubItem> {

    public ArrayList<Integer> dendriteWeightings;
    public Integer identifier;
    public int channel;
    public int numDendrites;

    public enum UpdateType {
        UI
    }

    public GraphSubItem(Integer identifier) {
        this.numDendrites = 4;
        this.dendriteWeightings = new ArrayList<>();
    }

    @Override
    public int getType() {
        return R.id.subitem_id;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.sub_item_interneuron;
    }

    @Override
    public ViewHolder getViewHolder(@NonNull View v) {
        return new ViewHolder(v);
    }

    protected static class ViewHolder extends FastAdapter.ViewHolder<GraphSubItem> {


        @BindView(R.id.dendrite1_seekbar)
        SeekBar dendrite1Seek;

        @BindView(R.id.dendrite2_seekbar)
        SeekBar dendrite2Seek;

        @BindView(R.id.dendrite3_seekbar)
        SeekBar dendrite3Seek;

        @BindView(R.id.dendrite4_seekbar)
        SeekBar dendrite4Seek;

        @BindView(R.id.dendrite1_text)
        TextView dendrite1Text;

        @BindView(R.id.dendrite2_text)
        TextView dendrite2Text;

        @BindView(R.id.dendrite3_text)
        TextView dendrite3Text;

        @BindView(R.id.dendrite4_text)
        TextView dendrite4Text;

        ArrayList<SeekBar> dendSeekBars;
        ArrayList<TextView> dendTextViews;
        ArrayList<StringHolder> dendStringHolder;
        StringHolder dendrite1Holder;
        StringHolder dendrite2Holder;

        public ViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }

        @Override
        public void bindView(GraphSubItem item, List<Object> payloads) {
            Log.d("bind w/ payload", payloads.toString());

            if (payloads.isEmpty()) {

                // initial binding

                dendSeekBars = new ArrayList<>(Arrays.asList(dendrite1Seek, dendrite2Seek, dendrite3Seek, dendrite4Seek));
                dendTextViews = new ArrayList<>(Arrays.asList(dendrite1Text, dendrite2Text, dendrite3Text, dendrite4Text));
                dendStringHolder = new ArrayList<>();

                for (int i = 0; i < item.numDendrites; i++) {
                    item.dendriteWeightings.add(dendSeekBars.get(i).getProgress());
                    dendStringHolder.add(new StringHolder(item.dendriteWeightings.get(i).toString()));
                    dendStringHolder.get(i).applyTo(dendTextViews.get(i));
                    dendSeekBars.get(i).setTag(dendTextViews.get(i));
                }

            } else if (payloads.contains(GraphSubItem.UpdateType.UI)) {
                for (int i = 0; i < item.numDendrites; i++) {
//                    Log.d("dend", item.dendriteWeightings.get(i).toString());
//                    dendStringHolder.get(i).setText(item.dendriteWeightings.get(i).toString());
                    dendTextViews.get(i).setText(item.dendriteWeightings.get(i).toString());
                }
            }
        }

        @Override
        public void unbindView(GraphSubItem item) {
        }
    }
}

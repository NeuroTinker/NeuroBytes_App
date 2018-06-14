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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by jarod on 2/9/18.
 */

public class GraphSubItem<Parent extends ISubItem> extends AbstractExpandableItem<GraphItem, GraphSubItem.ViewHolder, GraphSubItem> {

    public Integer dendrite1Weighting;

    public GraphSubItem() {
        this.dendrite1Weighting = 10000;
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

        StringHolder dendrite1Holder;

        public ViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }

        @Override
        public void bindView(GraphSubItem item, List<Object> payloads) {
            Log.d("bind w/ payload", payloads.toString());
            List<SeekBar> seekBars = Arrays.asList(dendrite1Seek, dendrite2Seek, dendrite3Seek, dendrite4Seek);
            List<TextView> textViews = Arrays.asList(dendrite1Text, dendrite2Text, dendrite3Text, dendrite4Text);

            dendrite1Holder = new StringHolder(Integer.toString(item.dendrite1Weighting));
            dendrite1Holder.applyTo(dendrite1Text);

//            dendrite1Seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//                @Override
//                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
//                    dendrite1Text.setText(i);
//                }
//
//                @Override
//                public void onStartTrackingTouch(SeekBar seekBar) {
//
//                }
//
//                @Override
//                public void onStopTrackingTouch(SeekBar seekBar) {
//
//                }
//            });

//            for (int j=0; j<4; j++) {
//                final int k = j;
//                seekBars.get(j).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//                    @Override
//                    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
//                        textViews.get(k).setText(i);
//                    }
//
//                    @Override
//                    public void onStartTrackingTouch(SeekBar seekBar) {
//
//                    }
//
//                    @Override
//                    public void onStopTrackingTouch(SeekBar seekBar) {
//
//                    }
//                });
//            }
        }

        @Override
        public void unbindView(GraphSubItem item) {
        }
    }
}

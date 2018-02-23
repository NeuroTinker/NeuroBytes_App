package com.neurotinker.neurobytes;

import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.ISubItem;
import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem;
import com.mikepenz.fastadapter.items.AbstractItem;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by jarod on 2/9/18.
 */

public class GraphSubItem<Parent extends ISubItem> extends AbstractExpandableItem<GraphItem, GraphSubItem.ViewHolder, GraphSubItem> {

    @Override
    public int getType() {
        return R.id.subitem_id;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.sub_item;
    }

    @Override
    public ViewHolder getViewHolder(@NonNull View v) {
        return new ViewHolder(v);
    }

    protected static class ViewHolder extends FastAdapter.ViewHolder<GraphSubItem> {

        @BindView(R.id.dendrite1_id)
        SeekBar dendrite1;

        @BindView(R.id.dendrite2_id)
        SeekBar dendrite2;

        @BindView(R.id.dendrite3_id)
        SeekBar dendrite3;

        @BindView(R.id.dendrite4_id)
        SeekBar dendrite4;

        public ViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }

        @Override
        public void bindView(GraphSubItem item, List<Object> payloads) {
            Log.d("bind w/ payload", payloads.toString());
        }

        @Override
        public void unbindView(GraphSubItem item) {
        }
    }
}

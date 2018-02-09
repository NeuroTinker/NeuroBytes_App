package com.neurotinker.neurobytes;

import android.support.annotation.NonNull;
import android.view.View;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.ISubItem;
import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem;
import com.mikepenz.fastadapter.items.AbstractItem;

import java.util.List;

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

        public ViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }

        @Override
        public void bindView(GraphSubItem item, List<Object> payloads) {

        }

        @Override
        public void unbindView(GraphSubItem item) {
        }
    }
}

package com.neurotinker.neurobytes;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.expandable.ExpandableExtension;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter_extensions.drag.ItemTouchCallback;
import com.mikepenz.fastadapter_extensions.drag.SimpleDragCallback;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static com.neurotinker.neurobytes.NidService.ACTION_ADD_CHANNEL;
import static com.neurotinker.neurobytes.NidService.ACTION_RECEIVED_DATA;
import static com.neurotinker.neurobytes.NidService.ACTION_REMOVE_CHANNEL;
import static com.neurotinker.neurobytes.NidService.BUNDLE_CHANNEL;
import static com.neurotinker.neurobytes.NidService.BUNDLE_DATA_POTENTIAL;
import static com.neurotinker.neurobytes.NidService.BUNDLE_DATA_TYPE;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link ChannelDisplayFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link ChannelDisplayFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ChannelDisplayFragment extends Fragment {
    private static final String TAG = ChannelDisplayFragment.class.getSimpleName();

    Context _context;
    private int chCnt = 0;
    private Queue<Integer> nextCh = new LinkedList<>();
    private boolean isPaused = false;
    private boolean nidIsRunning;
    private Map<Integer, GraphItem> channels = new HashMap<>();

    private ItemAdapter itemAdapter = new ItemAdapter();
    private FastAdapter fastAdapter = FastAdapter.with(itemAdapter);
    private RecyclerView recyclerView;
    private ExpandableExtension expandableExtension;

    private SimpleDragCallback dragCallback;
    private ItemTouchCallback itemTouchCallback;

    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private String mParam1;

    private OnFragmentInteractionListener mListener;

    public ChannelDisplayFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @return A new instance of fragment ChannelDisplayFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ChannelDisplayFragment newInstance(String param1, String param2) {
        ChannelDisplayFragment fragment = new ChannelDisplayFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        ConstraintLayout layout = (ConstraintLayout) inflater.inflate(
                R.layout.fragment_channel_display, container, false);

        /**
         * Initialize the RecyclerView
         */

        expandableExtension = new ExpandableExtension<>();
        expandableExtension.withOnlyOneExpandedItem(true);
        fastAdapter.addExtension(expandableExtension);
        fastAdapter.setHasStableIds(true);

//        itemTouchCallback = new ChannelTouchCallback();
//        fastAdapter.withEventHook(new AddChannelEventHook());
//        fastAdapter.withEventHook(new ClearChannelEventHook());

        recyclerView = (RecyclerView) layout.findViewById(R.id.recview);
        recyclerView.setLayoutManager(new LinearLayoutManager(_context));
        recyclerView.setAdapter(fastAdapter);

        dragCallback = new SimpleDragCallback();
        ItemTouchHelper touchHelper = new ItemTouchHelper(dragCallback);
        touchHelper.attachToRecyclerView(recyclerView);


        /**
         * Make the first item
         */
        GraphItem firstItem = new GraphItem(++chCnt);
        itemAdapter.add(firstItem);
//        fastAdapter.notifyAdapterItemChanged(0, firstItem);
//        channels.put(firstItem.channel, firstItem);
//        addItem();

        return layout;
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        _context = context;
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }

        // make the first item
//        GraphItem firstItem = new GraphItem(++chCnt);
//        itemAdapter.add(firstItem);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private GraphItem addItem() {
        GraphItem newItem = new GraphItem(
                nextCh.peek() != null ? nextCh.remove() : ++chCnt
        );
        itemAdapter.add(newItem);
        channels.put(newItem.channel, newItem);
        return newItem;
    }

    private GraphSubItem addSubItem(GraphItem item) {
        GraphSubItem newSubItem = new GraphSubItem();
        newSubItem.withParent(item);
        return newSubItem;
    }

    private final BroadcastReceiver nidReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_RECEIVED_DATA:
                    int ch = intent.getIntExtra(BUNDLE_CHANNEL, 0);

                    if (channels.containsKey(ch)) {
                        if (intent.hasExtra(BUNDLE_DATA_POTENTIAL)) {
                            int data = intent.getIntExtra(BUNDLE_DATA_POTENTIAL, 0);
                            channels.get(ch).graphController.update(data);
                        } else if (intent.hasExtra(BUNDLE_DATA_TYPE)) {
                            /**h
                             * Initialize newly acquired channel:
                             * 1. Check type and make channel visible
                             * 2. Make the subitem for the specific board
                             * 2. Store required references from the new item (e.g. graphController)
                             * 3. Send CHANNEL_ACQUIRED message
                             * 4. Make a new add-item channel
                             */
                            int type = intent.getIntExtra(BUNDLE_DATA_TYPE, 0);

                            if (channels.get(ch).state == GraphItem.GraphState.NEW) {
                                /**
                                 * Enable GraphItem
                                 * Make appropriate subitem and attach it to channel
                                 * TODO: Make subitem specific to board type
                                 */
                                channels.get(ch).channelController.setAcquired();
                                channels.get(ch).withSubItems(Arrays.asList(
                                        addSubItem(channels.get(ch))
                                ));
                                addItem();
                            } else {
                                Log.e(TAG, "Channel re-initialized");
                            }
                        }
                    } else {
                        Log.e(TAG, "Invalid channel");
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private void setFilters() {
        IntentFilter filter = new IntentFilter();

        filter.addAction(NidService.ACTION_NID_READY);
        filter.addAction(NidService.ACTION_NID_DISCONNECTED);
        filter.addAction(ACTION_RECEIVED_DATA);

        _context.registerReceiver(nidReceiver, filter);
    }

    /**
     * Drag and drop callback
     */
    private class ChannelTouchCallback implements ItemTouchCallback {
        @Override
        public boolean itemTouchOnMove(int oldPosition, int newPosition) {
            Collections.swap(itemAdapter.getAdapterItems(), oldPosition, newPosition); // change position
            fastAdapter.notifyAdapterItemMoved(oldPosition, newPosition);
            return true;
        }

        @Override
        public void itemTouchDropped(int oldPosition, int newPosition) {

        }
    }

    /**
     * Add channel button event hook
     */
    private class AddChannelEventHook extends ClickEventHook<GraphItem> {
        @Nullable
        @Override
        public View onBind(@NonNull RecyclerView.ViewHolder viewHolder) {
            if (viewHolder instanceof GraphItem.ViewHolder) {
                // bind click event to 'add' icon
                return viewHolder.itemView.findViewById(R.id.add_id);
            }
            return null;
        }

        @Override
        public void onClick(View v, int position, FastAdapter fastAdapter, GraphItem item) {
            if (nidIsRunning) {
                v.setVisibility(View.GONE);
                ((View) v.getParent()).findViewById(R.id.loading_id).setVisibility(View.VISIBLE);
                item.state = GraphItem.GraphState.WAITING;
                Intent intent = new Intent(ACTION_ADD_CHANNEL);
                intent.putExtra(BUNDLE_CHANNEL, item.channel);
                _context.sendBroadcast(intent);
            } else {
                ((View) v.getParent()).findViewById(R.id.nousb_id).setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * Clear channel button event hook
     */
    private class ClearChannelEventHook extends ClickEventHook<GraphItem> {
        @Nullable
        @Override
        public View onBind(@NonNull RecyclerView.ViewHolder viewHolder) {
            if (viewHolder instanceof GraphItem.ViewHolder) {
                // bind click event to 'add' icon
                return viewHolder.itemView.findViewById(R.id.clear_id);
            }
            return null;
        }

        @Override
        public void onClick(View v, int position, FastAdapter fastAdapter, GraphItem item) {
            //itemAdapter.remove(position);
            nextCh.add(item.channel);
            Intent intent = new Intent(ACTION_REMOVE_CHANNEL);
            intent.putExtra(BUNDLE_CHANNEL, item.channel);
            _context.sendBroadcast(intent);
            channels.remove(item);
            itemAdapter.remove(position);
            //fastAdapter.notifyAdapterDataSetChanged();
            //fastAdapter.notifyAdapterItemRemoved(position);
        }
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }
}

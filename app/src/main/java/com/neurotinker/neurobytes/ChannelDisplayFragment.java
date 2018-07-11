package com.neurotinker.neurobytes;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.IItem;
import com.mikepenz.fastadapter.ISubItem;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.expandable.ExpandableExtension;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter.listeners.CustomEventHook;
import com.mikepenz.fastadapter.listeners.TouchEventHook;
import com.mikepenz.fastadapter_extensions.drag.ItemTouchCallback;
import com.mikepenz.fastadapter_extensions.drag.SimpleDragCallback;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;

import static com.neurotinker.neurobytes.NidService.ACTION_CHANNEL_ACQUIRED;
import static com.neurotinker.neurobytes.NidService.ACTION_NID_DISCONNECTED;
import static com.neurotinker.neurobytes.NidService.ACTION_NID_READY;
import static com.neurotinker.neurobytes.NidService.ACTION_RECEIVED_DATA;
import static com.neurotinker.neurobytes.NidService.ACTION_REMOVE_CHANNEL;
import static com.neurotinker.neurobytes.NidService.ACTION_SEND_DATA;
import static com.neurotinker.neurobytes.NidService.BUNDLE_CHANNEL;
import static com.neurotinker.neurobytes.NidService.BUNDLE_DATA_PARAM;
import static com.neurotinker.neurobytes.NidService.BUNDLE_DATA_POTENTIAL;
import static com.neurotinker.neurobytes.NidService.BUNDLE_DATA_TYPE;
import static com.neurotinker.neurobytes.NidService.ACTION_ADD_CHANNEL;
import static com.neurotinker.neurobytes.NidService.BUNDLE_DATA_VALUE;


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
    private Timer updateTimer = new Timer();
    private Handler timerHandler = new Handler(Looper.getMainLooper());

    private ItemAdapter itemAdapter = new ItemAdapter();
    private FastAdapter fastAdapter = FastAdapter.with(itemAdapter);
    private RecyclerView recyclerView;
    private ExpandableExtension expandableExtension;
    private ArrayList<GraphSubItem> subItems;

    private SimpleDragCallback dragCallback;
    private ItemTouchCallback itemTouchCallback;

    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private String mParam1;

    private OnFragmentInteractionListener mListener;

    private static boolean nidRunning;
    NidService nidService;
    private static boolean nidBound = false;
    private static boolean sendDendWeightingFlag = false;

    private GraphView testGraph; // debug

    public ChannelDisplayFragment() {
        // Required empty public constructor
    }

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
//        fastAdapter.isSelectable();

        itemTouchCallback = new ChannelTouchCallback();
        fastAdapter.withEventHook(new AddChannelEventHook());
        fastAdapter.withEventHook(new ClearChannelEventHook());
        fastAdapter.withEventHook(new ResetDendritesEventHook());
        fastAdapter.withEventHook(new DendriteChangeEventHook(R.id.dendrite1_seekbar, 0));
        fastAdapter.withEventHook(new DendriteChangeEventHook(R.id.dendrite2_seekbar, 1));
        fastAdapter.withEventHook(new DendriteChangeEventHook(R.id.dendrite3_seekbar, 2));
        fastAdapter.withEventHook(new DendriteChangeEventHook(R.id.dendrite4_seekbar, 3));
//        fastAdapter.withEventHook(new SeekBarEventHook(R.id.dendrite1_seekbar, 0));
//        fastAdapter.withEventHook(new SeekBarEventHook(R.id.dendrite2_seekbar, 1));
//        fastAdapter.withEventHook(new SeekBarEventHook(R.id.dendrite3_seekbar, 2));
//        fastAdapter.withEventHook(new SeekBarEventHook(R.id.dendrite4_seekbar, 3));

        recyclerView = (RecyclerView) layout.findViewById(R.id.recview);
        recyclerView.setLayoutManager(new LinearLayoutManager(_context));
        recyclerView.setAdapter(fastAdapter);

        dragCallback = new SimpleDragCallback();
        ItemTouchHelper touchHelper = new ItemTouchHelper(dragCallback);
        touchHelper.attachToRecyclerView(recyclerView);


        /**
         * Make the first item
         */
        addItem();

        /**
         * Start the channel update timed task
         */
//        updateTimer.schedule(updateTask,1000, 200);
        timerHandler.postDelayed(updateRunnable, 2000);

        return layout;
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

        if (testGraph != null) testGraph.resume();

        // setup broadcast receivers
        Log.d(TAG, "trying to bind to NidService");
        Intent bindingIntent = new Intent(_context, NidService.class);
        _context.bindService(bindingIntent, nidConnection, Context.BIND_AUTO_CREATE);
        setFilters();
    }

    @Override
    public void onDetach() {
        for (GraphItem item : channels.values()) {
            item.channelController.pause();
        }
        _context.unbindService(nidConnection);
        _context.unregisterReceiver(nidReceiver);
        super.onDetach();
        mListener = null;
    }

    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            /**
             * Remove inactive items and update UI of active ones
             */
            for (int i = 0; i < fastAdapter.getItemCount(); i++) {
                Object iItem = fastAdapter.getItem(i);
                if (iItem instanceof GraphItem) {
//                    Log.d(TAG, "updating item");
                    GraphItem item = (GraphItem) iItem;
                    if (item.state != GraphItem.GraphState.NEW) {
                        if (item.channelController.count == 0) {
//                        removeItem(item);
                        } else {
//                            fastAdapter.notifyAdapterItemChanged(i, GraphItem.UpdateType.CHINFO);
                            fastAdapter.notifyItemChanged(i, GraphItem.UpdateType.CHINFO);
                        }
                        item.channelController.count = 0;
                    }
                } else if (iItem instanceof GraphSubItem) {
                    Log.d(TAG, "updating subitem");
//                    fastAdapter.notifyAdapterItemChanged(i, GraphSubItem.UpdateType.UI);
                    sendDendWeightingFlag = true;
                    fastAdapter.notifyItemChanged(i, GraphSubItem.UpdateType.UI);
                }
            }

            timerHandler.postDelayed(this, 200);
        }
    };

    private TimerTask updateTask = new TimerTask() {
        @Override
        public void run() {
            /**
             * Remove inactive items
             */
            // not on UI thread
            for (int i = 0; i < fastAdapter.getItemCount(); i++) {
                GraphItem item = (GraphItem) fastAdapter.getItem(i);
                if (item.channelController.count == 0 && item.state != GraphItem.GraphState.NEW) {
                } else {
                    fastAdapter.notifyAdapterItemChanged(i, GraphItem.UpdateType.CHINFO);
                }
                item.channelController.count = 0;
            }
        }
    };

    private GraphItem addItem() {
        GraphItem newItem = new GraphItem(
                nextCh.peek() != null ? nextCh.remove() : ++chCnt
        );
        // use the channel number for id
        newItem.withIdentifier(newItem.channel);
        itemAdapter.add(newItem);
        channels.put(newItem.channel, newItem);
        return newItem;
    }

    private void removeItem(GraphItem item) {
        nextCh.add(item.channel);
        Intent intent = new Intent(ACTION_REMOVE_CHANNEL);
        intent.putExtra(BUNDLE_CHANNEL, item.channel);
        _context.sendBroadcast(intent);
        int position = itemAdapter.getAdapterPosition(item);
        expandableExtension.collapse(position);
        channels.remove(item);
        itemAdapter.remove(position);
    }

    private GraphSubItem addSubItem(GraphItem item) {
        GraphSubItem newSubItem = new GraphSubItem(item.channel);
        newSubItem.withParent(item);
        return newSubItem;
    }

    private final BroadcastReceiver nidReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_NID_READY:
                    nidIsRunning = true;
                    break;
                case ACTION_NID_DISCONNECTED:
                    nidIsRunning = false;
                    break;
                case ACTION_CHANNEL_ACQUIRED:
                    Log.d(TAG, "new channel acquired");
                    break;
                case ACTION_RECEIVED_DATA:
                    int ch = intent.getIntExtra(BUNDLE_CHANNEL, 0);

                    if (channels.containsKey(ch)) {
                        if (intent.hasExtra(BUNDLE_DATA_POTENTIAL)) {
                            int data = intent.getIntExtra(BUNDLE_DATA_POTENTIAL, 0);
                            channels.get(ch).channelController.update(data);
                        } else if (intent.hasExtra(BUNDLE_DATA_TYPE)) {
                            /**
                             * Initialize newly acquired channel:
                             * 1. Check type and make channel visible
                             * 2. Make the subitem for the specific board
                             * 2. Store required references from the new item (e.g. graphController)
                             * 3. Send CHANNEL_ACQUIRED message
                             * 4. Make a new add-item channel
                             */
                            int type = intent.getIntExtra(BUNDLE_DATA_TYPE, 0);

                            if (channels.get(ch).state == GraphItem.GraphState.WAITING) {
                                /**
                                 * Enable GraphItem
                                 * Make appropriate subitem and attach it to channel
                                 * TODO: Make subitem specific to board type
                                 */
                                Log.d(TAG, String.format("Channel acquired %d", ch));
                                channels.get(ch).state = GraphItem.GraphState.ACQUIRED;
                                channels.get(ch).channelController.setAcquired();
                                channels.get(ch).withSubItems(Arrays.asList(
                                        addSubItem(channels.get(ch))
                                ));
                                addItem();
                            } else {
                                Log.d(TAG, "invalid channel acquisition");
                            }
                        }
                    } else {
                        Log.e(TAG, String.format("invalid channel %d", ch));
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private void setFilters() {
        IntentFilter filter = new IntentFilter();

        filter.addAction(ACTION_NID_READY);
        filter.addAction(ACTION_NID_DISCONNECTED);
        filter.addAction(ACTION_CHANNEL_ACQUIRED);
        filter.addAction(ACTION_RECEIVED_DATA);

        _context.registerReceiver(nidReceiver, filter);
    }

    private final ServiceConnection nidConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            NidService.NidBinder binder = (NidService.NidBinder) iBinder;
            nidService = binder.getService();
            nidBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            nidBound = false;
        }
    };

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

    private class TestEventHook extends ClickEventHook<GraphItem> {
        @Override
        public void onClick(View v, int position, FastAdapter<GraphItem> fastAdapter, GraphItem item) {

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
            Log.d(TAG, "adding new channel");
            if (nidIsRunning) {
                v.setVisibility(View.GONE);
                ((View) v.getParent()).findViewById(R.id.loading_id).setVisibility(View.VISIBLE);
                item.state = GraphItem.GraphState.WAITING;
                Intent intent = new Intent(ACTION_ADD_CHANNEL);
                intent.putExtra(BUNDLE_CHANNEL, item.channel);
                _context.sendBroadcast(intent);
            } else {
                Log.d(TAG, "onClick() NID not running");
                Toast.makeText(_context, "NID not Running", Toast.LENGTH_SHORT).show();
                // TODO: NID not running indication
//                ((View) v.getParent()).findViewById(R.id.nousb_id).setVisibility(View.VISIBLE);
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
            removeItem(item);
            //fastAdapter.notifyAdapterDataSetChanged();
            //fastAdapter.notifyAdapterItemRemoved(position);
        }
    }

    private class ResetDendritesEventHook extends ClickEventHook<GraphSubItem> {
        @Nullable
        @Override
        public View onBind(@NonNull RecyclerView.ViewHolder viewHolder) {
            if (viewHolder instanceof GraphSubItem.ViewHolder) {
                Log.d(TAG, "subview found!");
                return viewHolder.itemView.findViewById(R.id.button_reset);
            }
            return null;
        }

        @Override
        public void onClick(View v, int position, FastAdapter fastAdapter, GraphSubItem item) {
        }
    }

    private class DendriteChangeEventHook extends TouchEventHook<GraphSubItem> {

        private int seekBarId;
        private int dendNum;

        public DendriteChangeEventHook(int seekBarId, int dendNum) {
            this.seekBarId = seekBarId;
            this.dendNum = dendNum;
        }

        @Nullable
        @Override
        public View onBind(@NonNull RecyclerView.ViewHolder viewHolder) {
            if (viewHolder instanceof GraphSubItem.ViewHolder) {
                return viewHolder.itemView.findViewById(seekBarId);
            }
            return null;
        }

        @Override
        public boolean onTouch(@NonNull View v, MotionEvent motion, int position, FastAdapter fastAdapter, GraphSubItem item) {
            Log.d(TAG, "touch");
            SeekBar seekBar = (SeekBar) v;
            item.dendriteWeightings.set(dendNum, seekBar.getProgress());
            fastAdapter.notifyItemChanged(position, GraphSubItem.UpdateType.UI);
            Intent intent = new Intent(ACTION_SEND_DATA);
            intent.putExtra(BUNDLE_CHANNEL, item.channel);
            intent.putExtra(BUNDLE_DATA_PARAM, dendNum);
            intent.putExtra(BUNDLE_DATA_VALUE, seekBar.getProgress());
            if (sendDendWeightingFlag) {
                _context.sendBroadcast(intent);
                sendDendWeightingFlag = false;
            }
            return false;
        }
    }

    private class SeekBarEventHook extends CustomEventHook<GraphSubItem> {
        /**
         * Updates the textView next to the seekBar with the new dendrite weighting.
         * DendriteChangeEventHook updates the item's internal dendrite weightings.
         */
        int seekId;
        int dendNum;
        private Timer sendMessageTimer = new Timer();
        private List<SeekBar> seekBarList;
        private List<TextView> textViewList;

        public SeekBarEventHook(int seekId, int dendNum) {
            this.seekId = seekId;
            this.dendNum = dendNum;
        }

//        private ISubItem getSubItem(RecyclerView.ViewHolder viewHolder) {
//            viewHolder.itemView.getTag()
//        }

        @Nullable
        @Override
        public View onBind(@NonNull RecyclerView.ViewHolder viewHolder) {
            if (viewHolder instanceof GraphSubItem.ViewHolder) {
                return viewHolder.itemView.findViewById(seekId);
            }
            return null;
        }

        @Override
        public void attachEvent(View view, RecyclerView.ViewHolder viewHolder) {
            Object iItem = getItem(viewHolder);
            FastAdapter fastAdapter = getFastAdapter(viewHolder);
            if (fastAdapter != null) {
                Log.d(TAG, fastAdapter.toString());
                Log.d(TAG, Integer.toString(fastAdapter.getItemCount()));
                Log.d(TAG, Integer.toString(fastAdapter.getHolderAdapterPosition(viewHolder)));
            } else {
                Log.d(TAG, "adapter not found");
            }
            ((SeekBar) view).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
//                    ((TextView) viewHolder.itemView.findViewById(R.id.dendrite1_text)).setText(Integer.toString(i));
//                    ((TextView) seekBar.getTag()).setText(Integer.toString(i));
//                    ((TextView) ((View) seekBar.getParent()).findViewById(R.id.dendrite1_text)).setText(Integer.toString(i));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
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

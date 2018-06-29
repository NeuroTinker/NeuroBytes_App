package com.neurotinker.neurobytes;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

// TODO: Make this a surface view to get graph rendering off of the UI thread
public class GraphView extends SurfaceView implements Runnable{

    private final String TAG = GraphView.class.getSimpleName();

    private Thread thread;
    private int pathIndex = 0;
    private int prevData;
    private int numPoints;
    private int yMax = 12000;
    private int yZeroPixel;
    private int yMin = -12000;
    private float xUnitPixels;
    private float yUnitPixels;
    private ArrayList<Path> pathLists = new ArrayList<>();
    private Queue<Integer> dataQueue = new LinkedList<>();
    private Queue<Path> pathQueue = new LinkedList<>();
    private Path dataPath = new Path();
    private Paint graphPaint = new Paint();
    private int dataTop = 0;
    private boolean updated = false;
    private float startX = 0;
    private float startY = 0;
    private int width;
    private int height;
    private Bitmap buffBitmap;
    private Canvas buffCanvas;
    private boolean running;

    private void init() {
        graphPaint.setStyle(Paint.Style.STROKE);
        graphPaint.setStrokeWidth(5F);
        graphPaint.setAntiAlias(true);
    }

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Get attribute values
        TypedArray arr = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.GraphView,
                0, 0
        );

        init();

        try {
            numPoints = arr.getInteger(R.styleable.GraphView_numPoints, 240);
            graphPaint.setStrokeWidth(arr.getInteger(R.styleable.GraphView_lineWeight, 5));
            graphPaint.setColor(arr.getInteger(R.styleable.GraphView_lineColor, Color.BLACK));
        } finally {
            arr.recycle();
        }

    }

    public void update(int data) {
        if (!updated || running == false) {
            buffCanvas.drawColor(Color.WHITE);
            // scroll the whole graph
            for (Path path : pathQueue) {
                path.offset(-1 * xUnitPixels, 0);
            }

            // add the new data point path
            if (++dataTop > 1) {
                Path path = new Path();
//            int x1 = (int) ((numPoints - 1) * xUnitPixels);
                float x1 = width - xUnitPixels;
//            int x2 = (int) (numPoints * xUnitPixels);
                float x2 = width;

                float y1 = height - ((prevData - yMin) * yUnitPixels);
                float y2 = height - ((data - yMin) * yUnitPixels);

                path.moveTo(x1, y1);
                path.lineTo(x2, y2);
                pathQueue.add(path);
                if (++dataTop > numPoints) {
                    dataQueue.remove();
                    dataTop -= 1;
                }
            }
            dataQueue.add(data);
            prevData = data;
            updated = true;
            invalidate();
        }
    }

    public void redraw() {

        // TODO: recompute all paths

        invalidate();

    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;

        // calculate new data point widths
        xUnitPixels = width / numPoints;
        yUnitPixels = (float)height / (float) (yMax - yMin); // assumes yMin < 0
        yZeroPixel = (int)(yMax * yUnitPixels);

        Log.d(TAG, String.format("xPixels %f, yPixels %f, yZero %d", xUnitPixels, yUnitPixels, yZeroPixel));

        // update size for buffered bitmap
        buffBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        if (buffCanvas == null) {
            buffCanvas = new Canvas();
        }
        buffCanvas.setBitmap(buffBitmap);

        // redraw on size change
        redraw();
    }

    public void pause() {
        running = false;
        try{
            thread.join();
        } catch (InterruptedException e) {

        }
    }

    public void resume() {
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    @Override
    public void run() {
        Canvas canvas;

        while (running) {
            if (updated && getHolder().getSurface().isValid()) {
                canvas = getHolder().lockCanvas();
                // clear the buffered canvas
                for (Path path : pathQueue) {
                    buffCanvas.drawPath(path, graphPaint);
                }
                canvas.drawBitmap(buffBitmap, 0, 0, graphPaint);
//                canvas.drawColor(Color.WHITE);
                getHolder().unlockCanvasAndPost(canvas);
                buffCanvas.drawColor(Color.WHITE);
                updated = false;
            }
        }
    }
//
//    @Override
//    protected void onDraw(Canvas canvas) {
//        super.onDraw(canvas);
//
//        if (updated && getHolder().getSurface().isValid()) {
//            canvas = getHolder().lockCanvas();
//            // clear the buffered canvas
//            for (Path path : pathQueue) {
//                buffCanvas.drawPath(path, graphPaint);
//            }
//            canvas.drawBitmap(buffBitmap, 0, 0, graphPaint);
//            getHolder().unlockCanvasAndPost(canvas);
//            buffCanvas.drawColor(Color.WHITE);
//            updated = false;
//        }
//    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
//                updateIndex(createPath(event));
                update(dataTop * 50);
                break;
            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                float y = event.getY();
//                Path path = pathLists.get(pathIndex - 1);;
//                path.lineTo(x, y);
                break;
            default:
                break;
        }
        // Invalidate the whole view. If the view is visible.
//        invalidate();
        return true;
    }
}

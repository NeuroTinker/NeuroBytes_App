package com.neurotinker.neurobytes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

public class GraphView extends View {

    private int pathIndex = 0;
    private ArrayList<Path> pathLists = new ArrayList<>();
    private ArrayList<Paint> paintLists = new ArrayList<>();
    private float startX = 0F;
    private float startY = 0F;

    private void init() {
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3F);
        paintLists.add(paint);
        Path path = new Path();
        path.moveTo(startX, startY);
        pathLists.add(path);
        pathIndex++;

    }

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(Color.WHITE);

        for (int i = 0; i < pathIndex; i++) {
            Path path = pathLists.get(i);
            Paint paint = paintLists.get(i);

            canvas.drawPath(path, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
//                updateIndex(createPath(event));
                break;
            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                float y = event.getY();
                Path path = pathLists.get(pathIndex - 1);;
                path.lineTo(x, y);
                break;
            default:
                break;
        }
        // Invalidate the whole view. If the view is visible.
        invalidate();
        return true;
    }
}

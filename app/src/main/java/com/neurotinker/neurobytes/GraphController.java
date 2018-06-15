package com.neurotinker.neurobytes;

import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;

import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.util.ExponentialBackOff;

import static android.support.constraint.Constraints.TAG;
import static android.view.View.VISIBLE;


public class GraphController {

    private final String TAG = GraphController.class.getSimpleName();

    LineChart chart; // = (LineChart) findViewById(R.id.chart);
    List<Entry> entries = new ArrayList<Entry>();
    LineDataSet dataSet;
    LineData lineData;
    int numPoints = 500;
    private int nextPotential = 0;
    public int count = 0;
    public boolean enabled = false;
    private boolean isFiring = true;
    public int fireCount = 0;
    public double firingRate = 0.0;


    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        Random rand = new Random();

        @Override
        public void run() {
            timerHandler.postDelayed(this, 10);
        }
    };

    public void PotentialGraph(LineChart ch){
        PotentialGraph(ch, Color.BLUE);
    }

    public void PotentialGraph(LineChart ch, int clr) {
        chart = ch;

        ch.setDrawGridBackground(false);
        ch.getXAxis().setDrawGridLines(false);
        ch.getAxisLeft().setDrawGridLines(false);
        ch.getAxisRight().setDrawGridLines(false);

        dataSet = new LineDataSet(entries, "Label");
        for (int i=0; i<numPoints; i++) {
            dataSet.addEntry(new Entry(dataSet.getEntryCount(), 0));
        }
        dataSet.setColor(clr);
        dataSet.setCircleRadius(1);
        dataSet.setCircleColor(Color.BLACK);
        dataSet.setValueTextColor(4);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(3);
        dataSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        dataSet.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);

        chart.getXAxis().setDrawAxisLine(false);
        chart.getXAxis().setDrawLabels(false);
        chart.getAxisLeft().setDrawAxisLine(false);
        chart.getAxisLeft().setDrawLabels(false);
        chart.getAxisLeft().setEnabled(false);
        YAxis yAxis = chart.getAxisRight();
        yAxis.setAxisMinimum(-12000);
        yAxis.setAxisMaximum(12000);
        chart.setVisibleYRange(-12000, 12000, YAxis.AxisDependency.RIGHT);

        // temp disable right axis
        chart.getAxisRight().setDrawLabels(false);
        chart.getAxisRight().setDrawAxisLine(false);

        LimitLine ll = new LimitLine(10000, "");
        ll.setLineColor(Color.RED);
        ll.setLineWidth(2);
        ll.setTextColor(Color.BLACK);
        ll.setTextSize(12f);
        ll.enableDashedLine(10f,10f,0f);
        //yAxis.setDrawTopYLabelEntry(true);

        //yAxis.addLimitLine(ll);

        chart.setScaleYEnabled(false);
        chart.setScaleEnabled(false);

        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);

        lineData = new LineData(dataSet);

        chart.setData(lineData);
        chart.invalidate();
        // start graph updater
        timerHandler.postDelayed(timerRunnable, 0);
    }

    public void update(int potential) {

        LineData data = chart.getData();

        if (data != null) {
            ILineDataSet set = data.getDataSetByIndex(0);

            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }

            if (set.getEntryForIndex(set.getEntryCount()-100).getY() >= 10000 &&
                    set.getEntryForIndex(set.getEntryCount()-99).getY() < 0)
                fireCount -= 1; // this looks hideous

            if (set.getEntryForIndex(set.getEntryCount()-1).getY() >= 10000
                    && potential < 0)
                fireCount += 1;

            this.firingRate = fireCount / 5.0;

            data.addEntry(new Entry(set.getEntryCount(), potential), 0);

            chart.notifyDataSetChanged();

            chart.setVisibleXRangeMaximum(240); // 120 at 20 hz
            //YAxis yAxis = chart.getAxisRight();
            chart.setVisibleYRange(-12000, 12000, YAxis.AxisDependency.RIGHT);
            chart.moveViewToX(data.getEntryCount());
            //chart.moveViewTo(data.getEntryCount(), 0, YAxis.AxisDependency.RIGHT);
            chart.setVisibleYRange(-12000, 12000, YAxis.AxisDependency.RIGHT);
            chart.invalidate();
        }

        count += 1;
    }

    public void enable() {
        this.enabled = true;
        ((View) chart.getParent().getParent()).setVisibility(VISIBLE);
    }

    public void disable() {
        this.enabled = false;
        //((View) chart.getParent().getParent()).setVisibility(View.GONE);
    }

    public void clear() {
        LineData data = chart.getData();
        ILineDataSet set = data.getDataSetByIndex(0);

        for (int i=0; i<240; i++){
            data.addEntry(new Entry(set.getEntryCount(), 0), 0);
        }

        chart.notifyDataSetChanged();

        chart.setVisibleXRangeMaximum(240); // 120 at 20 hz
        //YAxis yAxis = chart.getAxisRight();
        chart.setVisibleYRange(-12000, 12000, YAxis.AxisDependency.RIGHT);
        chart.moveViewToX(data.getEntryCount());
        //chart.moveViewTo(data.getEntryCount(), 0, YAxis.AxisDependency.RIGHT);
        chart.setVisibleYRange(-12000, 12000, YAxis.AxisDependency.RIGHT);
        chart.invalidate();
    }

    private LineDataSet createSet() {

        LineDataSet set = new LineDataSet(null, "Dynamic Data");
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        set.setColor(ColorTemplate.getHoloBlue());
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleRadius(4f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }
}

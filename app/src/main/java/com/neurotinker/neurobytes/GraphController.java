package com.neurotinker.neurobytes;

import android.graphics.Color;
import android.os.Handler;
import android.util.Log;

import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;


public class GraphController {

    LineChart chart; // = (LineChart) findViewById(R.id.chart);
    List<Entry> entries = new ArrayList<Entry>();
    LineDataSet dataSet;
    LineData lineData;
    int numPoints = 500;
    private int nextPotential = 0;


    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        Random rand = new Random();

        @Override
        public void run() {
            //update(rand.nextInt(10000) + 5);
            //update(nextPotential);
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

            data.addEntry(new Entry(set.getEntryCount(), potential), 0);

            chart.notifyDataSetChanged();

            //chart.notifyDataSetChanged();

            chart.setVisibleXRangeMaximum(240); // 120 at 20 hz
            //YAxis yAxis = chart.getAxisRight();
            chart.setVisibleYRange(-12000, 12000, YAxis.AxisDependency.RIGHT);
            chart.moveViewToX(data.getEntryCount());
            //chart.moveViewTo(data.getEntryCount(), 0, YAxis.AxisDependency.RIGHT);
            chart.setVisibleYRange(-12000, 12000, YAxis.AxisDependency.RIGHT);
            chart.invalidate();
            //yAxis.setAxisMinimum(-12000);
            //yAxis.setAxisMaximum(12000);
            //Log.d("Graph", "updates");

        }
    }

    public void addPoint(int potential) {
        nextPotential = potential;
    }

    public int checkForEvent() {
        Random rand = new Random();
        int impulse = rand.nextInt(120);
        if (impulse > 110){
            return -100 * impulse;
        } else if (impulse > 95){
            return impulse * 100;
        }
        return 0;
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

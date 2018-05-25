package com.neurotinker.neurobytes;

public class RtPlot {
    /**
     * Controller for real-time neuron plotting
     *
     * Wraps the plot-lib OpenGL ES Library
     */

    static {
        System.loadLibrary("native-lib");
    }

    /**
     * @param width the current view width
     * @param height the current view height
     */
    public static native void init(int width, int height);
    public static native void step();
    public static native void addPoint(float point);
}

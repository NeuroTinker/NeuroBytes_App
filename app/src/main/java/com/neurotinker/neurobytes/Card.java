package com.neurotinker.neurobytes;

/**
 * Created by jarod on 2/6/18.
 */

public class Card {
    private GraphController graphController;
    private String name;
    private int channel;

    public Card(int ch) {
        this.channel = ch;
    }

    public void clearGraph() {
        graphController.clear();
    }
}

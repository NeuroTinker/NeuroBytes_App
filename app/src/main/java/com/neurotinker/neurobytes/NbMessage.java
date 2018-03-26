package com.neurotinker.neurobytes;

import android.os.Message;

/**
 * Created by jrwhi on 3/24/2018.
 */

public class NbMessage {

    public static final int HEADER_OFFSET = 27;
    public static final int CHANNEL_OFFSET = 20;
    private final byte DATA_HEADER = 0b1010;
    private short[] packet;
    public boolean isValid;
    private int header;
    public int subheader;
    public int parameter;
    public int channel;
    public int data;

    /**
     * Parse the message into an NbMessage object
     * @param packet
     */
    public NbMessage(short[] packet) {
        /**
         * Parse using shorts
         */
        this.packet = packet;
        byte header =  (byte) ((packet[0] & 0b1111100000000000) >> 11);
        this.isValid = (header == DATA_HEADER);
        this.channel = (packet[0] & 0b0000011111100000) >> 5;
        this.header =  (packet[0] & 0b1111000000000000) >> 11;
        this.data = packet[1];
    }

    public boolean checkIfValid() {
        return this.isValid;
    }

    private void parseUsingShorts(short [] msg) {
        short headers = msg[0];
        channel = (headers & 0b0000011111100000) >> 5;
        header =  (headers & 0b1111100000000000) >> 11;
    }
}

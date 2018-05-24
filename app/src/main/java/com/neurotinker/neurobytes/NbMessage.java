package com.neurotinker.neurobytes;

import android.os.Message;

/**
 * Created by jrwhi on 3/24/2018.
 */

public class NbMessage {

    public static final int HEADER_OFFSET = 27;
    public static final int CHANNEL_OFFSET = 20;
    private final byte DATA_HEADER = 0b1010;

    public enum Subheader {
        POTENTIAL   ((byte) 0b000),
        TYPE        ((byte) 0b001),
        UNIQUE_ID   ((byte) 0b010),
        MODE        ((byte) 0b011),
        PARAMETER   ((byte) 0b100);

        private final byte val;
        Subheader(byte val) {
            this.val = val;
        }

        Subheader(int val) {
            this.val = (byte) val;
        }
    }

    private short[] packet;
    public boolean isValid;
    public int header;
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
        byte header =  (byte) ((packet[0] & 0b1111000000000000) >> 12);
        this.isValid = (header == DATA_HEADER);
        this.subheader = (packet[0] & 0b0000111000000000) >> 9;
        this.channel = (packet[0] & 0b0000000111000000) >> 6;
        this.header =  (packet[0] & 0b1111000000000000) >> 12;
        this.data = packet[1];
    }

    public boolean checkIfValid() {
        return this.isValid;
    }

    public boolean checkSubheader(Subheader sub) {
        return (sub.val == (byte) this.subheader);
    }

    private void parseUsingShorts(short [] msg) {
        short headers = msg[0];
        channel = (headers & 0b0000011111100000) >> 5;
        header =  (headers & 0b1111100000000000) >> 11;
    }
}

package com.neurotinker.neurobytes;

/**
 * Created by jrwhi on 3/24/2018.
 */

public enum NidMessage {
    BLINK_MESSAGE   (Header.BLINK_HEADER),
    PING_MESSAGE    (Header.PING_HEADER);

    public enum Header

        BLINK_HEADER    ((byte) 0b1001),
        PING_HEADER     ((byte) 0b1110),
        GLOBAL_HEADER   ((byte) 0b1100),
        SELECTED_HEADER ((byte) 0b1101),
        DATA_HEADER     ((byte) 0b1010);

        private final byte val;
        Header(byte val) {
            this.val = val;
        }
    }

    private final Header header;
    NidMessage(Header header) {
        this.header = header;
    }
}

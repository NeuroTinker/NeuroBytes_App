package com.neurotinker.neurobytes;

/**
 * Created by jrwhi on 3/24/2018.
 */

public class NidMessage {

    private enum Header{
        /**
         * Message headers
         */
        BLINK_HEADER    ((byte) 0b1001),
        PING_HEADER     ((byte) 0b1110),
        GLOBAL_HEADER   ((byte) 0b1100),
        SELECTED_HEADER ((byte) 0b1101),
        DATA_HEADER     ((byte) 0b1010);

        private final byte val;
        private final int offset = 27;
        private final int bitLength = 4;
        Header(byte val) {
            this.val = val;
        }
    }

    private enum GlobalCommandHeader {
        IDENTIFY_HEADER ((byte) 0b001),
        VERSION_HEADER  ((byte) 0b010),
        PAUSE_HEADER    ((byte) 0b011),
        ZERO_HEADER     ((byte) 0b100),
        SPAN_HEADER     ((byte) 0b101);

        private final byte val;
        private final int offset = 21;
        private final int bitLength = 6;
        GlobalCommandHeader(byte val) {
            this.val = val;
        }
    }

    private class Message {
        public Header header;

        public byte[] getBytes() {
            return new byte[] {
                    (byte) (this.header.val << 4),
                    0x0,
                    0x0,
                    0x0
            };
        }
    }

    public class BlinkMessage extends Message {
        public BlinkMessage() {
            this.header = Header.BLINK_HEADER;
        }
    }

    public class PingMessage extends Message {
        public PingMessage() {
            this.header = Header.PING_HEADER;
        }
    }

    public class GlobalCommandMessage extends Message {
        public final GlobalCommandHeader command;

        public GlobalCommandMessage(GlobalCommandHeader command) {
            this.header = Header.GLOBAL_HEADER;
            this.command = command;
        }

        @Override
        public byte[] getBytes() {
            return new byte[] {
                    (byte) ((this.header.val << 4) | (this.command.val >> 2)),
                    (byte) (this.command.val << 6),
                    0x0,
                    0x0
            };
        }
    }

    public class IdentifyMessage extends GlobalCommandMessage {
        private int channel;

        public IdentifyMessage(int channel) {
            super(GlobalCommandHeader.IDENTIFY_HEADER);
            this.channel = channel;
        }

        @Override
        public byte[] getBytes() {
            return new byte[] {
                    (byte) ((this.header.val << 4) | (this.command.val >> 2)),
                    (byte) ((this.command.val << 6) | (this.channel << 3)),
                    0x0,
                    0x0
            };
        }
    }
}

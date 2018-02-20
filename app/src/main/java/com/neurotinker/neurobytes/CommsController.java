package com.neurotinker.neurobytes;

/**
 * Created by jarod on 2/16/18.
 */

public class CommsController {

    private static final byte[] blinkMessage = new byte[] {
            (byte) 0b10010000,
            0x0,
            0x0,
            0x0
    };

    /*
    Identify message to network.
     */

    private byte[] makeIdentifyMessage(int ch) {
        //byte b = (byte) ch;
        byte chByte = (byte) ch;
        return new byte[] {
                (byte) 0b11000000,
                (byte) (0b01000000 | (byte) ((byte)(chByte & 0b111) << 3)),
                0x0,
                0x0

        };
    }

    /*
    Data message to a channel.
    4-bit header
    3-bit channel
    5-bit parameter id
    16-bit value
     */

    private byte[] makeDataMessage(int ch, int param, int val) {
        byte chByte = (byte) ch;
        byte paramByte = (byte) param;
        byte valByte1 = (byte) (val & 0xFF);
        byte valByte2 = (byte) ((val >> 8) & 0xFF);
        return new byte[] {
                (byte) (0b11010000 | (byte) chByte << 1),
                (byte) ((paramByte << 4) | ((valByte1 & 0b11110000) >> 4)),
                (byte) (((valByte1 & 0b1111) << 4) | ((valByte2 & 0b11110000) >> 4)),
                (byte) ((valByte2 & 0b1111) << 4)
        };
    }

}

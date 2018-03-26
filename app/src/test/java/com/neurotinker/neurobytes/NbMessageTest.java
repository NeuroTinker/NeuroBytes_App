package com.neurotinker.neurobytes;

import android.os.Message;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by jrwhi on 3/24/2018.
 */
public class NbMessageTest {
    @Test
    public void parseTest() throws Exception {
        short headers = 0b1111 << (NbMessage.HEADER_OFFSET - 16);
        short channel = 0b111 << (NbMessage.CHANNEL_OFFSET - 16);
        short data = 10000;
        short [] packet = {headers, data};
        NbMessage nbMsg = new NbMessage(packet);
        boolean valid = nbMsg.checkIfValid();
        assertEquals(true, valid);
    }
}
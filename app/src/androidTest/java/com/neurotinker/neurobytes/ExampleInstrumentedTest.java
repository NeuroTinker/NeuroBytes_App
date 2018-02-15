package com.neurotinker.neurobytes;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    private final short ch1Header = -24544;
    private final short ch2Header = -24512;

    @Test
    public void useAppContext() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();
        short headers = ch1Header;
        int channel = (headers & 0b0000000111100000) >> 5;
        assertEquals(1, channel);
        channel = (ch2Header & 0b0000000111100000) >> 5;
        assertEquals(2, channel);

        assertEquals("com.neurotinker.neurobytes", appContext.getPackageName());
    }
}

package com.neurotinker.neurobytes;

import android.content.Intent;
import android.os.IBinder;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeoutException;

/**
 * Created by jarod on 3/29/18.
 */

@RunWith(AndroidJUnit4.class)
public class NidServiceTest {
    @Rule
    public final ServiceTestRule serviceTestRule = new ServiceTestRule();

    @Test
    public void testWithBoundService() throws TimeoutException {
        Intent startIntent =
                new Intent(InstrumentationRegistry.getTargetContext(),
                        NidService.class);
        IBinder binder = serviceTestRule.bindService(startIntent);

        NidService service = ((NidService.LocalBinder) binder).getService();
    }
}

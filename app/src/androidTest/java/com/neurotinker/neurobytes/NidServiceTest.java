package com.neurotinker.neurobytes;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeoutException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

/**
 * Created by jarod on 3/29/18.
 */

@RunWith(AndroidJUnit4.class)
public class NidServiceTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getTargetContext();
    }

    @Test (expected = NullPointerException.class)
    public void onBind() throws Exception {
        final NidService service = new NidService();
        context.startService(new Intent(context, NidService.class));

        service.onBind(new Intent());
    }

    @Test
    public void onStartCommand() throws Exception {
        final NidService service = new NidService();
        context.startService(new Intent(context, NidService.class));

        assertEquals(Service.START_STICKY, service.onStartCommand(new Intent(), 0, 0));
    }

    @Test
    public void onCreate() throws Exception {
        final NidService service = new NidService();

        context.startService(new Intent(context, NidService.class));

        //service.onStartCommand(new Intent(), 0, 0);
        service.onCreate();
        assertTrue(NidService.isServiceStarted());
        assertEquals(NidService.State.NOT_CONNECTED, service.state);
    }

    @Test
    public void onDestroy() throws Exception {
        final NidService service = new NidService();
        context.startService(new Intent(context, NidService.class));

        service.onDestroy();
        assertFalse(NidService.isServiceStarted());
    }
/*
    @Test (timeout=1000)
    public void testWithStartedService() throws TimeoutException{
        Intent startIntent =
                new Intent(InstrumentationRegistry.getTargetContext(), NidService.class);
        serviceTestRule.startService(startIntent);

        assertTrue(NidService.isStarted);
    }

    @Test (timeout=1000)
    public void testWithBoundService() throws TimeoutException {
        Intent startIntent =
                new Intent(InstrumentationRegistry.getTargetContext(), NidService.class);
        IBinder binder = serviceTestRule.bindService(startIntent);
        assertNotNull(binder);

        NidService service = ((NidService.NidBinder) binder).getService();

        assertEquals(NidService.State.NOT_CONNECTED, service.state);
    }*/
}

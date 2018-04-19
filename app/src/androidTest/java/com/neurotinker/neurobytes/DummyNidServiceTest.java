package com.neurotinker.neurobytes;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

/**
 * Created by jarod on 3/29/18.
 */

@RunWith(AndroidJUnit4.class)
public class DummyNidServiceTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getTargetContext();
    }

    @Test (expected = NullPointerException.class)
    public void onBind() throws Exception {
        final DummyNidService service = new DummyNidService();
        context.startService(new Intent(context, DummyNidService.class));

        service.onBind(new Intent());
    }

    @Test
    public void onStartCommand() throws Exception {
        final DummyNidService service = new DummyNidService();
        context.startService(new Intent(context, DummyNidService.class));

        assertEquals(Service.START_STICKY, service.onStartCommand(new Intent(), 0, 0));
    }

    @Test
    public void onCreate() throws Exception {
        final DummyNidService service = new DummyNidService();

        context.startService(new Intent(context, DummyNidService.class));

        //service.onStartCommand(new Intent(), 0, 0);
        service.onCreate();
        assertTrue(DummyNidService.isServiceStarted());
        assertEquals(DummyNidService.State.RUNNING, service.state);
    }

    @Test
    public void onDestroy() throws Exception {
        final DummyNidService service = new DummyNidService();
        context.startService(new Intent(context, DummyNidService.class));

        service.onDestroy();
        assertFalse(DummyNidService.isServiceStarted());
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

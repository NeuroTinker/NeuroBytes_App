package com.neurotinker.neurobytes;

import android.app.Service;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class UsbServiceTest {

    @Rule
    private final ServiceTestRule serviceTestRule = new ServiceTestRule();

    @Test(timeout=1000)
    public void testAsboundService() throws TimeoutException{
    }

}

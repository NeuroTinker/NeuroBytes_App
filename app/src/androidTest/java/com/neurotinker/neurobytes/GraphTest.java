package com.neurotinker.neurobytes;

import android.os.Handler;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

/**
 * Created by jarod on 2/9/18.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class GraphTest {

    public static final int MESSAGE_FROM_SERIAL_PORT = 0;

    private static final int test_ch = 1;
    private static final int num_test_points = 500;

    private static final short [] test_message = {0b1000000, 100}; // channel 1 data=100

    @Rule
    public final ActivityTestRule<GraphPotential> mainActivityRule =
            new ActivityTestRule<>(GraphPotential.class, true, false);

    @Test(timeout=5000)
    public void graphTest() throws Exception {
        // start activity
        mainActivityRule.launchActivity(null);
        // click create channel button
        onView(withId(R.id.fab1)).perform(click());

        // send dummy data
        //((Handler)mainActivityRule.getActivity().mHandler)
         //       .obtainMessage(MESSAGE_FROM_SERIAL_PORT, test_message)
          //      .sendToTarget();
    }
}

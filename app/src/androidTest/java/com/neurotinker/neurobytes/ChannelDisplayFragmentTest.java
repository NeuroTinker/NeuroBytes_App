package com.neurotinker.neurobytes;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

/**
 * Created by jarod on 3/27/18.
 */

@RunWith(AndroidJUnit4.class)
public class ChannelDisplayFragmentTest {

    @Rule
    public ActivityTestRule<MainActivity> activityActivityTestRule =
            new ActivityTestRule<MainActivity>(MainActivity.class);

    @Before
    public void init() {
        activityActivityTestRule.getActivity().getSupportFragmentManager().beginTransaction();
    }

    @Test
    public void inflateFragmentTest() throws Exception {
        onView(withId(R.id.channelfragment_id)).check(matches((isDisplayed())));

    }
}

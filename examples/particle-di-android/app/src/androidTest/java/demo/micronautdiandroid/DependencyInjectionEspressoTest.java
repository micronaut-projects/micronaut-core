package demo.micronautdiandroid;

import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class DependencyInjectionEspressoTest {

    @Rule
    public ActivityTestRule<MainActivity> mActivityRule =
            new ActivityTestRule(MainActivity.class);

    @Test
    public void dependencyInjectionWorks() {
        onView(withText(expected())).check(matches(isDisplayed()));
    }

    static String expected() {
        StringBuilder sb = new StringBuilder();
        sb.append("Books Fetched #2");
        sb.append("\n");
        sb.append("Book Practical Grails 3");
        sb.append("\n");
        sb.append("Book Grails 3 - Step by Step");
        sb.append("\n");
        return sb.toString();
    }
}

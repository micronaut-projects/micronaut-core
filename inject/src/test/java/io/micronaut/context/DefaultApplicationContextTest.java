package io.micronaut.context;

import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;
import spock.lang.Issue;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultApplicationContextTest {
    @Issue("https://github.com/micronaut-projects/micronaut-test/issues/615#issuecomment-1516355815")
    @Test
    public void applicationContextShouldShutDownTheEnvironmentItCreated() {
        DefaultApplicationContext ctx = new DefaultApplicationContext();
        ctx.start();
        Environment env = ctx.getEnvironment();
        assertTrue(env.isRunning());
        ctx.stop();
        assertFalse(env.isRunning(), "expected to be stopped");
    }

    @Test
    public void applicationContextShouldNotStopTheEnvironmentItDidNotCreate() {
        DefaultApplicationContext ctx = new DefaultApplicationContext();
        // make DefaultApplicationContext create and stop it's managed environment,
        // so it thinks it manages it
        ctx.start();
        ctx.stop();

        // providing ctx with an external environment
        ApplicationContext ctx2 = ApplicationContext.run();
        ctx.setEnvironment(ctx2.getEnvironment());

        ctx.start();
        ctx.stop();
        assertTrue(ctx2.getEnvironment().isRunning(), "shouldn't stop an external environment");

        ctx2.stop(); //cleanup
    }
}

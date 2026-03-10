package io.micronaut.context;

import org.junit.jupiter.api.Test;
import spock.lang.Issue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultBeanContextContainsBeanRaceTest {

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/11768")
    @Test
    void containsBeanDoesNotThrowWhenCacheIsClearedConcurrently() throws InterruptedException {
        DefaultBeanContext context = (DefaultBeanContext) ApplicationContext.run();
        Map<?, Boolean> containsBeanCache = context.containsBeanCache;
        context.containsBean(MissingBean.class);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread lookupThread = new Thread(() -> {
            while (running.get()) {
                try {
                    context.containsBean(MissingBean.class);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                    break;
                }
            }
        });
        Thread clearThread = new Thread(() -> {
            while (running.get()) {
                containsBeanCache.clear();
            }
        });

        lookupThread.start();
        clearThread.start();

        for (int i = 0; i < 50000 && failure.get() == null; i++) {
            context.containsBean(MissingBean.class);
        }

        running.set(false);
        lookupThread.join();
        clearThread.join();
        context.stop();

        assertNull(failure.get());
    }

    static class MissingBean {
    }
}

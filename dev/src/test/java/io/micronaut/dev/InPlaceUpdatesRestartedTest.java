package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextLifeCycle;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadCompletedEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.inject.BeanDefinition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a new generation's context refused while it was told of the restart that started it needs no other restart: its
 * beans were built from the classes of that change. What it refused before, as a refresh can while it starts, does.
 */
class InPlaceUpdatesRestartedTest {

    @Test
    void onlyWhatTheListenersOfTheRestartRefusedIsLeftOut() {
        try (ApplicationContext context = ApplicationContext.builder().beanDependencyTrackingEnabled(true).start()) {
            BeanDefinition<?> application = runningApplication(context);
            InPlaceUpdates updates = new InPlaceUpdates(null);
            context.registerSingleton(ApplicationEventListener.class, refusingListener(context, application));

            updates.restarted(context, restartCompleted());

            assertFalse(updates.restartRequired(context));
            // a refusal after it, as of a refresh, needs a restart
            assertTrue(((DefaultBeanContext) context).refuseStoppingApplication(application, "recreating"));
            assertTrue(updates.restartRequired(context));
        }
    }

    @Test
    void aRefusalBeforeTheRestartWasToldStillNeedsARestart() {
        try (ApplicationContext context = ApplicationContext.builder().beanDependencyTrackingEnabled(true).start()) {
            BeanDefinition<?> application = runningApplication(context);
            InPlaceUpdates updates = new InPlaceUpdates(null);
            // a configuration client's refresh, as the context started
            assertTrue(((DefaultBeanContext) context).refuseStoppingApplication(application, "recreating"));
            context.registerSingleton(ApplicationEventListener.class, refusingListener(context, application));

            updates.restarted(context, restartCompleted());

            assertTrue(updates.restartRequired(context));
        }
    }

    @Test
    void aRefreshThatAskedForARestartWhileTheRestartWasToldIsNotLeftOut() {
        try (ApplicationContext context = ApplicationContext.builder().beanDependencyTrackingEnabled(true).start()) {
            BeanDefinition<?> application = runningApplication(context);
            InPlaceUpdates updates = new InPlaceUpdates(null);
            // a configuration client's refresh on another thread, refused while the restart's listeners run
            context.registerSingleton(ApplicationEventListener.class, (ApplicationEventListener<Object>) event -> {
                if (event instanceof ReloadCompletedEvent) {
                    ((DefaultBeanContext) context).refuseStoppingApplication(application, "recreating");
                    updates.requestRestart(context);
                }
            });

            updates.restarted(context, restartCompleted());

            assertTrue(updates.restartRequired(context));
        }
    }

    private static BeanDefinition<?> runningApplication(ApplicationContext context) {
        context.registerSingleton(RunningApplication.class, new RunningApplication(context));
        return context.getBeanRegistration(RunningApplication.class, null).getBeanDefinition();
    }

    private static ApplicationEventListener<Object> refusingListener(ApplicationContext context, BeanDefinition<?> application) {
        // registered without its event type: told of every event, it refuses for the completed reload alone
        return event -> {
            if (event instanceof ReloadCompletedEvent) {
                ((DefaultBeanContext) context).refuseStoppingApplication(application, "recreating");
            }
        };
    }

    private static ReloadCompletedEvent restartCompleted() {
        ClassChangeEvent change = new ClassChangeEvent(InPlaceUpdatesRestartedTest.class, Set.of(), InPlaceUpdatesRestartedTest.class.getClassLoader(), List.of(), ReloadStrategy.RESTART);
        return new ReloadCompletedEvent(InPlaceUpdatesRestartedTest.class, change, List.of(), List.of(), Duration.ZERO);
    }

    static final class RunningApplication implements ApplicationContextLifeCycle<RunningApplication> {
        private final ApplicationContext context;

        RunningApplication(ApplicationContext context) {
            this.context = context;
        }

        @Override
        public ApplicationContext getApplicationContext() {
            return context;
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public RunningApplication start() {
            return this;
        }

        @Override
        public RunningApplication stop() {
            return this;
        }
    }
}

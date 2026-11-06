package io.micronaut.aop.runtimetarget;

import io.micronaut.aop.runtime.DefaultRuntimeProxyDefinition;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.DependentBeanProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A runtime proxy resolves its target on every call, through what it retained when it was created, and calls come
 * from any number of threads: no call may see another, or anything a failed call left behind.
 */
class RuntimeProxyTargetRaceTest {

    private static final long TIMEOUT_SECONDS = 30;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void cleanup() {
        TargetProbe.onCreation = () -> { };
        executor.shutdownNow();
    }

    @Test
    void aTargetResolvedWhileAnotherIsBeingInjectedIsNotACircularDependency() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            InjectingTarget proxy = context.getBean(InjectingTarget.class);
            Held held = holdFirstCreation();

            Future<Boolean> first = executor.submit(proxy::injected);
            held.awaitEntered();
            List<String> failures = new ArrayList<>();
            call(proxy::injected, failures);
            held.release();
            assertTrue(get(first));

            assertEquals(List.of(), failures);
        }
    }

    @Test
    void concurrentCallsOfATargetWithInjectionAllSucceedAndLaterCallsStillDo() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            InjectingTarget proxy = context.getBean(InjectingTarget.class);
            assertEquals(List.of(), callConcurrently(proxy::injected));
        }
    }

    @Test
    void concurrentCallsOfATargetWithNothingToInjectAllSucceedAndLaterCallsStillDo() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            InitializingTarget proxy = context.getBean(InitializingTarget.class);
            assertEquals(List.of(), callConcurrently(proxy::ready));
        }
    }

    @Test
    void aTargetResolvedOnEveryCallIsNotKeptByTheProxy() {
        try (ApplicationContext context = ApplicationContext.run()) {
            InjectingTarget proxy = context.getBean(InjectingTarget.class);
            for (int call = 0; call < 100; call++) {
                assertTrue(proxy.injected());
            }

            DefaultRuntimeProxyDefinition<?> definition = (DefaultRuntimeProxyDefinition<?>) context.getBean(PerCallTargetProxyCreator.class)
                .definitions.get(InjectingTarget.class);
            assertTrue(definition.resolutionContext().getDependentBeans().isEmpty());
        }
    }

    @Test
    void aTargetResolvedWhileTheProxyIsCreatedAndWhatItReceivedAreDestroyedWithTheProxy() {
        try (ApplicationContext context = ApplicationContext.run()) {
            CachingTarget.TARGETS_DESTROYED.set(0);
            CachingTarget.Dependency.CREATED.set(0);
            CachingTarget.Dependency.DESTROYED.set(0);
            BeanRegistration<CachingTarget> registration = context.getBeanRegistration(CachingTarget.class, null);

            context.destroyBean(registration);

            assertEquals(1, CachingTarget.TARGETS_DESTROYED.get());
            assertEquals(CachingTarget.Dependency.CREATED.get(), CachingTarget.Dependency.DESTROYED.get());
        }
    }

    @Test
    void onlyATargetResolvedWhileItsProxyIsCreatedIsOwnedWhenTheProxyIsInjected() {
        try (ApplicationContext context = ApplicationContext.run()) {
            CachingTarget.TARGETS_DESTROYED.set(0);
            CachingTarget.Dependency.CREATED.set(0);
            CachingTarget.Dependency.DESTROYED.set(0);
            BeanRegistration<ProxyHolder> registration = context.getBeanRegistration(ProxyHolder.class, null);

            // the two proxies, and not the target that the one called once it existed resolved in the same resolution
            assertEquals(2, ((DependentBeanProvider) registration).dependentBeans().size());
            context.destroyBean(registration);

            assertEquals(1, CachingTarget.TARGETS_DESTROYED.get());
            assertEquals(CachingTarget.Dependency.CREATED.get(), CachingTarget.Dependency.DESTROYED.get());
        }
    }

    @Test
    void aProxyCreatedWithoutARegistrationDoesNotKeepTheContextCreatingIt() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            InjectingTarget proxy = context.createBean(InjectingTarget.class);
            assertEquals(List.of(), callConcurrently(proxy::injected));
            assertNotNull(context.createBean(CachingTarget.class));
        }
    }

    /**
     * Holds the first creation that runs the probe open until it is released.
     */
    private static Held holdFirstCreation() {
        Held held = new Held();
        AtomicBoolean first = new AtomicBoolean();
        TargetProbe.onCreation = () -> {
            if (first.compareAndSet(false, true)) {
                held.hold();
            }
        };
        return held;
    }

    private List<String> callConcurrently(Callable<Boolean> call) throws InterruptedException {
        List<Callable<Boolean>> callers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            callers.add(() -> {
                for (int n = 0; n < 5_000; n++) {
                    assertTrue(call.call());
                }
                return true;
            });
        }
        List<String> failures = new ArrayList<>();
        for (Future<Boolean> caller : executor.invokeAll(callers)) {
            call(() -> get(caller), failures);
        }
        call(call, failures);
        return failures.stream().distinct().limit(5).toList();
    }

    private void call(Callable<Boolean> call, List<String> failures) throws InterruptedException {
        try {
            assertTrue(get(executor.submit(call)));
        } catch (ExecutionException e) {
            failures.add(String.valueOf(e.getCause()));
        }
    }

    private static <T> T get(Future<T> future) throws InterruptedException, ExecutionException {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("Timed out", e);
        }
    }

    private static final class Held {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        void hold() {
            entered.countDown();
            try {
                released.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void awaitEntered() throws InterruptedException {
            assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }

        void release() {
            released.countDown();
        }
    }
}

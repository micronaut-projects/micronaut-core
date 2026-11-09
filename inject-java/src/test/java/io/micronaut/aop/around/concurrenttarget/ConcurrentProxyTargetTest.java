package io.micronaut.aop.around.concurrenttarget;

import io.micronaut.aop.runtime.RuntimeProxyDefinition;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentProxyTargetTest {

    @Test
    void targetCachedWhileTheProxyIsCreatedIsDestroyedWithIt() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "ConcurrentProxyTargetTest"))) {
            BeanRegistration<PrototypeService> registration = context.getBeanRegistration(PrototypeService.class, null);
            Object target = context.getBean(CapturingRuntimeProxy.class).cachedTarget;
            assertNotNull(target);
            assertNotSame(registration.bean(), target);

            context.destroyBean(registration);

            assertTrue(PrototypeService.DESTROYED.contains(target));
        }
    }

    @Test
    void prototypeTargetsResolveConcurrently() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "ConcurrentProxyTargetTest"))) {
            PrototypeService first = context.getBean(PrototypeService.class);
            RuntimeProxyDefinition<?> definition = context.getBean(CapturingRuntimeProxy.class).definition;
            assertNotNull(definition);

            int threads = 16;
            int iterations = 200;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            try {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    futures.add(executor.submit(() -> {
                        start.await();
                        for (int j = 0; j < iterations; j++) {
                            PrototypeService target = (PrototypeService) definition.targetBean();
                            assertNotNull(target.repositoryA.repositoryC);
                            assertNotNull(target.repositoryB.repositoryC);
                            assertNotNull(target.repositoryC);
                            assertNotSame(first, target);
                        }
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> future : futures) {
                    future.get();
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }
}

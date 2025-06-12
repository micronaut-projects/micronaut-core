package io.micronaut.management.health.indicator.threads;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.management.endpoint.health.DetailsVisibility;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.aggregator.DefaultHealthAggregator;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class DeadlockedThreadsHealthIndicatorTest {
    @Test
    void diskSpaceHealthIndicatorViaConfiguration() {
        Map<String, Object> configuration = Map.of("endpoints.health.deadlocked-threads.enabled", StringUtils.FALSE);
        try (ApplicationContext context = ApplicationContext.run(configuration)) {
            assertFalse(context.containsBean(DeadlockedThreadsHealthIndicator.class));
        }
        // enabled by default
        try (ApplicationContext context = ApplicationContext.run()) {
            assertTrue(context.containsBean(DeadlockedThreadsHealthIndicator.class));
        }
    }

    @Test
    void testDeadlockedThreadsHealthIndicator() {
        Map<String, Object> configuration = Map.of(
                "spec.name", "DeadlockedThreadsHealthIndicatorTest",
                "endpoints.health.details-visible", DetailsVisibility.ANONYMOUS
        );
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, configuration)) {
            try  (HttpClient httpClient = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
                BlockingHttpClient client = httpClient.toBlocking();
                await().until(() -> isDown(client));
                Argument ok = Argument.of(Map.class);
                Argument notOk = Argument.of(Map.class);
                HttpClientResponseException ex = assertThrows(HttpClientResponseException.class,
                        () -> client.exchange(HttpRequest.GET("/health"), ok, notOk));
                assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
                Optional<Map> healthStatusOptional = ex.getResponse().getBody(notOk);
                assertNotNull(healthStatusOptional);
                assertTrue(healthStatusOptional.isPresent());
                Map healthStatus = healthStatusOptional.get();
                assertNotNull(healthStatus);
                assertEquals("DOWN", healthStatus.get("status"));
                assertEquals("DOWN", ((Map) ((Map) healthStatus.get("details")).get("deadlockedThreads")).get("status"));

                Map details =  (Map)((List) ((Map) ((Map) healthStatus.get("details")).get("deadlockedThreads")).get("details")).get(0);
                assertTrue(details.containsKey("threadId"));
                assertTrue(details.containsKey("threadName"));
                assertTrue(details.containsKey("threadState"));
                assertTrue(details.containsKey("daemon"));
                assertTrue(details.containsKey("priority"));
                assertTrue(details.containsKey("suspended"));
                assertTrue(details.containsKey("inNative"));
                assertTrue(details.containsKey("lockName"));
                assertTrue(details.containsKey("lockOwnerName"));
                assertTrue(details.containsKey("lockOwnerId"));
                assertFalse(details.containsKey("lockedSynchronizers"));
                assertTrue(details.containsKey("stackTrace"));
            }
        }
    }

    private boolean isDown(BlockingHttpClient client) {
        try {
            client.exchange("/health");
            return false;
        } catch (HttpClientResponseException e) {
            return true;
        }
    }

    @Requires(property = "spec.name", value = "DeadlockedThreadsHealthIndicatorTest")
    @Singleton
    static class DeadLockBean implements io.micronaut.context.event.ApplicationEventListener<StartupEvent> {
        private static final Logger LOG = LoggerFactory.getLogger(DeadLockBean.class);
        public void onApplicationEvent(StartupEvent event) {
            final Object lock1 = new Object();
            final Object lock2 = new Object();

            // Thread 1
            Thread thread1 = new Thread(() -> {
                synchronized (lock1) {
                    LOG.trace("Thread 1: Holding lock 1");

                    try {
                        // Introducing a delay to make deadlock more likely
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        LOG.error("", e);
                    }

                    synchronized (lock2) {
                        LOG.trace("Thread 1: Holding lock 1 and lock 2");
                    }
                }
            });
            // Thread 2
            Thread thread2 = new Thread(() -> {
                synchronized (lock2) {
                    LOG.trace("Thread 2: Holding lock 2");

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        LOG.error("", e);
                    }

                    synchronized (lock1) {
                        LOG.trace("Thread 2: Holding lock 2 and lock 1");
                    }
                }
            });
            thread1.start();
            thread2.start();
        }
    }
}

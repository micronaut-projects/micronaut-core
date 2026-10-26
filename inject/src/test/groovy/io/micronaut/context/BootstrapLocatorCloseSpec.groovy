package io.micronaut.context

import io.micronaut.context.annotation.BootstrapContextCompatible
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.BootstrapPropertySourceLocator
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.PropertySourcesLocator
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Closing the bootstrap locator does not wait for a load of the bootstrap configuration in
 * progress. On 5.2.x the context stop waited for it, for as long as a distributed configuration
 * server took to answer.
 */
@Timeout(60)
class BootstrapLocatorCloseSpec extends Specification {

    static final long WAIT_SECONDS = 10

    def setup() {
        ParkingLocator.instances.clear()
        ParkingLocator.park = false
        ParkingLocator.entered = new CountDownLatch(1)
        ParkingLocator.release = new CountDownLatch(1)
    }

    def cleanup() {
        ParkingLocator.release.countDown()
    }

    void "the context stops while a refresh reads the bootstrap configuration"() {
        given:
        ApplicationContext ctx = ApplicationContext.builder()
                .properties(['spec.name': 'BootstrapLocatorCloseSpec'])
                .bootstrapEnvironment(true)
                .start()

        when: "a refresh waits in the bootstrap context's locator"
        ParkingLocator.park = true
        CompletableFuture<Void> refresh = CompletableFuture.runAsync { ctx.environment.refresh() }

        then:
        ParkingLocator.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)

        when:
        CompletableFuture<Void> stop = CompletableFuture.runAsync { ctx.stop() }

        then: "the stop returns without waiting for the refresh, and closes the bootstrap context"
        stop.get(WAIT_SECONDS, TimeUnit.SECONDS) == null
        ParkingLocator.running() == 0

        when:
        ParkingLocator.park = false
        ParkingLocator.release.countDown()
        awaitQuietly(refresh)

        then: "the refresh that ends after the stop leaves no bootstrap context running"
        ParkingLocator.created() == 1
        ParkingLocator.running() == 0

        when:
        ctx.start()

        then: "a start after the stop reads the bootstrap configuration with a new bootstrap context"
        ParkingLocator.created() == 2
        ParkingLocator.running() == 1

        cleanup:
        ctx.close()
    }

    void "a load that ends after the locator is closed closes the bootstrap context it has created"() {
        given: "a context that has not started, and its bootstrap locator"
        ApplicationContext ctx = ApplicationContext.builder()
                .properties(['spec.name': 'BootstrapLocatorCloseSpec'])
                .bootstrapEnvironment(true)
                .build()
        PropertySourcesLocator locator = ((DefaultApplicationContext) ctx).@bootstrapPropertySourcesLocator

        when: "a load waits in the locator of the bootstrap context it has created"
        ParkingLocator.park = true
        CompletableFuture<Collection<PropertySource>> load = CompletableFuture.supplyAsync { locator.load(ctx.environment) }

        then:
        ParkingLocator.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)

        when:
        CompletableFuture<Void> close = CompletableFuture.runAsync { ((Closeable) locator).close() }

        then: "the close returns without waiting for the load"
        close.get(WAIT_SECONDS, TimeUnit.SECONDS) == null

        when:
        ParkingLocator.park = false
        ParkingLocator.release.countDown()
        load.get(WAIT_SECONDS, TimeUnit.SECONDS)

        then: "the load closes the bootstrap context it has created"
        ParkingLocator.created() == 1
        ParkingLocator.running() == 0

        when: "the next load creates a bootstrap context again, and keeps it"
        locator.load(ctx.environment)

        then:
        ParkingLocator.created() == 2
        ParkingLocator.running() == 1

        cleanup:
        ((Closeable) locator).close()
        ctx.close()
    }

    private static void awaitQuietly(CompletableFuture<?> future) {
        try {
            future.get(WAIT_SECONDS, TimeUnit.SECONDS)
        } catch (java.util.concurrent.ExecutionException ignored) {
            // a refresh that ends after the stop may fail on the closed bootstrap context
        }
    }

    @BootstrapContextCompatible
    @Singleton
    @Requires(property = "spec.name", value = "BootstrapLocatorCloseSpec")
    static class ParkingLocator implements BootstrapPropertySourceLocator {
        static final List<ParkingLocator> instances = new CopyOnWriteArrayList<>()
        static volatile boolean park
        static volatile CountDownLatch entered
        static volatile CountDownLatch release

        volatile boolean destroyed

        ParkingLocator() {
            instances.add(this)
        }

        static int created() {
            instances.size()
        }

        static int running() {
            instances.count { !it.destroyed }
        }

        @PreDestroy
        void destroy() {
            destroyed = true
        }

        @Override
        Iterable<PropertySource> findPropertySources(Environment environment) {
            if (park) {
                entered.countDown()
                release.await(BootstrapLocatorCloseSpec.WAIT_SECONDS * 3, TimeUnit.SECONDS)
            }
            return List.of()
        }
    }
}

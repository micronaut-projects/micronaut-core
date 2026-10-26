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
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Closing the bootstrap locator does not wait for a load of the bootstrap configuration in
 * progress. On 5.2.x the context stop waited for it, for as long as a distributed configuration
 * server took to answer. A load in progress closes the bootstrap context once it ends, and a load
 * that a stop has overtaken creates none.
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

    void "the context stops while a refresh reads the bootstrap configuration, and the refresh closes the bootstrap context when it ends"() {
        given:
        ApplicationContext ctx = startContext()

        when: "a refresh waits in the bootstrap context's locator"
        ParkingLocator.park = true
        CompletableFuture<Void> refresh = CompletableFuture.runAsync { ctx.environment.refresh() }

        then:
        ParkingLocator.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)

        when:
        CompletableFuture<Void> stop = CompletableFuture.runAsync { ctx.stop() }

        then: "the stop returns without waiting for the refresh, and leaves the bootstrap context it uses to it"
        stop.get(WAIT_SECONDS, TimeUnit.SECONDS) == null
        ParkingLocator.running() == 1

        when:
        ParkingLocator.park = false
        ParkingLocator.release.countDown()
        refresh.get(WAIT_SECONDS, TimeUnit.SECONDS)

        then: "the refresh closes the bootstrap context when it ends"
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
        ApplicationContext ctx = buildContext()
        PropertySourcesLocator locator = ((DefaultApplicationContext) ctx).@bootstrapPropertySourcesLocator

        when: "the environment starts, and its load waits in the locator of the bootstrap context it has created"
        ParkingLocator.park = true
        CompletableFuture<Void> start = CompletableFuture.runAsync { ctx.environment.start() }

        then:
        ParkingLocator.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)

        when:
        CompletableFuture<Void> close = CompletableFuture.runAsync { ((Closeable) locator).close() }

        then: "the close returns without waiting for the load"
        close.get(WAIT_SECONDS, TimeUnit.SECONDS) == null

        when:
        ParkingLocator.park = false
        ParkingLocator.release.countDown()
        start.get(WAIT_SECONDS, TimeUnit.SECONDS)

        then: "the load closes the bootstrap context it has created"
        ParkingLocator.created() == 1
        ParkingLocator.running() == 0

        when: "the next load creates a bootstrap context again, and keeps it"
        ctx.environment.refresh()

        then:
        ParkingLocator.created() == 2
        ParkingLocator.running() == 1

        cleanup:
        // the context itself never started, so its close may not stop the environment or close the locator
        ctx.environment.stop()
        ((Closeable) locator).close()
        ctx.close()
    }

    void "a load that a stop has overtaken creates no bootstrap context"() {
        given: "a context that has stopped, and its bootstrap locator"
        ApplicationContext ctx = startContext()
        PropertySourcesLocator locator = ((DefaultApplicationContext) ctx).@bootstrapPropertySourcesLocator
        ctx.stop()

        expect:
        ParkingLocator.created() == 1
        ParkingLocator.running() == 0

        when: "a refresh that started before the stop reaches the bootstrap locator after it"
        Collection<PropertySource> sources = locator.load(ctx.environment)

        then: "it reads nothing, and no bootstrap context is left running"
        sources.isEmpty()
        ParkingLocator.created() == 1
        ParkingLocator.running() == 0

        cleanup:
        ctx.close()
    }

    void "a start waits for a load of the bootstrap configuration that a stop has overtaken, so the two do not read it at the same time"() {
        given:
        ApplicationContext ctx = startContext()

        when: "a refresh waits in the bootstrap context's locator, and the context stops"
        ParkingLocator.park = true
        CompletableFuture<Void> refresh = CompletableFuture.runAsync { ctx.environment.refresh() }
        assert ParkingLocator.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        ctx.stop()

        and: "the context starts again"
        Thread starter = new Thread({ ctx.start() }, 'starter')
        starter.daemon = true
        starter.start()

        then: "the start waits for the load in progress, and has created no bootstrap context of its own"
        new PollingConditions(timeout: WAIT_SECONDS).eventually {
            assert starter.state == Thread.State.BLOCKED
        }
        ParkingLocator.created() == 1

        when:
        ParkingLocator.park = false
        ParkingLocator.release.countDown()
        refresh.get(WAIT_SECONDS, TimeUnit.SECONDS)
        starter.join(WAIT_SECONDS * 1000L)

        then: "the load closes the bootstrap context it used, and the start reads with a new one"
        !starter.alive
        ParkingLocator.created() == 2
        ParkingLocator.running() == 1

        cleanup:
        ParkingLocator.release.countDown()
        if (!starter.alive) {
            ctx.close()
        }
    }

    private static ApplicationContext startContext() {
        buildContext().start()
    }

    private static ApplicationContext buildContext() {
        ApplicationContext.builder()
                .properties(['spec.name': 'BootstrapLocatorCloseSpec'])
                .bootstrapEnvironment(true)
                .build()
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

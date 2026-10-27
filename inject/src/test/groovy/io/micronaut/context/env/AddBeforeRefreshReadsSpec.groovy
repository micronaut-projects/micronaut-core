package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A property source that another thread adds while a refresh holds the lock of the catalogs, before
 * the refresh reads the property sources, waits for the refresh. The refresh reads it in its order,
 * and the add then does not process it a second time, on top of the others.
 */
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/14159")
@Timeout(60)
class AddBeforeRefreshReadsSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "a property source added just before a refresh reads the property sources is processed once, in its order: #trigger"() {
        given: "a property source of a higher order that sets the same key"
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(PropertySource.of('higher', ['shared': 'higher'], 0))
        env.start()
        PropertySource lower = PropertySource.of('lower', ['shared': 'lower', 'lower.key': 'lower'], -10)

        when: "it is added while the refresh holds the lock, before it stops the environment"
        Thread refresher = parkIn(env.stopping) { trigger == 'refreshAndDiff' ? env.refreshAndDiff() : env.refresh() }
        Thread adder = daemon('adder') { env.addPropertySource(lower) }
        awaitWaiting(adder)
        release(env.stopping, refresher, adder)

        then: "the property source of the higher order wins"
        env.getProperty('shared', String).get() == 'higher'
        env.getProperty('lower.key', String).get() == 'lower'
        env.propertySources.any { it.is(lower) }

        cleanup:
        env.stopping.release.countDown()
        env.stop()

        where:
        trigger << ['refresh', 'refreshAndDiff']
    }

    void "a property source added once a refresh has read the property sources is processed on top, as before"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(PropertySource.of('higher', ['shared': 'higher'], 0))
        env.start()
        PropertySource lower = PropertySource.of('lower', ['shared': 'lower'], -10)

        when: "it is added once the refresh has read, before it publishes"
        Thread refresher = parkIn(env.started) { env.refresh() }
        Thread adder = daemon('adder') { env.addPropertySource(lower) }
        awaitWaiting(adder)
        release(env.started, refresher, adder)

        then:
        env.getProperty('shared', String).get() == 'lower'

        cleanup:
        env.started.release.countDown()
        env.stop()
    }

    void "a property source that was read, changed and added again is processed again, on top, as before"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        Map<String, Object> values = ['shared': 'first']
        PropertySource changing = PropertySource.of('changing', values, -10)
        env.addPropertySource(PropertySource.of('higher', ['shared': 'higher'], 0))
        env.addPropertySource(changing)
        env.start()
        env.refresh()

        when:
        values.put('shared', 'changed')
        env.addPropertySource(changing)

        then:
        env.getProperty('shared', String).get() == 'changed'

        cleanup:
        env.stop()
    }

    void "a property source added just before a refresh that then fails is processed once the refresh has failed, as before"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(PropertySource.of('higher', ['shared': 'higher'], 0))
        env.start()
        PropertySource lower = PropertySource.of('lower', ['shared': 'lower', 'lower.key': 'lower'], -10)

        when:
        env.failing.set(true)
        Thread refresher = parkIn(env.stopping) {
            try {
                env.refresh()
            } catch (IllegalStateException ignored) {
                // expected
            }
        }
        Thread adder = daemon('adder') { env.addPropertySource(lower) }
        awaitWaiting(adder)
        release(env.stopping, refresher, adder)

        then: "the refresh published nothing, and the add processed it"
        env.getProperty('lower.key', String).get() == 'lower'
        env.getProperty('shared', String).get() == 'lower'

        cleanup:
        env.stopping.release.countDown()
        env.stop()
    }

    void "a property source added while a refresh runs is processed once the add gets the lock, after a #after, as before"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        FailingSource failing = new FailingSource()
        env.addPropertySource(failing)
        env.addPropertySource(PropertySource.of('higher', ['shared': 'higher'], 0))
        env.start()
        PropertySource lower = PropertySource.of('lower', ['lower.someKey': 'lower'], -10)
        Thread adder = null

        when: "the add waits for the lock of the catalogs while a refresh runs, and then a stop"
        env.holdingCatalogLock {
            adder = daemon('adder') { env.addPropertySource(lower) }
            awaitWaiting(adder)
            env.refresh()
            env.stop()
            if (after == 'failed restart') {
                failing.armed.set(true)
                try {
                    env.start()
                } catch (IllegalStateException ignored) {
                    // expected
                }
            }
        }
        adder.join(WAIT_SECONDS * 1000L)

        then: "the add processed its property source, which the stop had dropped"
        !adder.alive
        env.getProperty('lower.some-key', String).get() == 'lower'

        cleanup:
        env.stop()

        where:
        after << ['stop', 'failed restart']
    }

    void "a property source added while a refresh runs is processed once, in its order, when a refresh that fails follows"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        FailingSource failing = new FailingSource()
        env.addPropertySource(failing)
        env.addPropertySource(PropertySource.of('higher', ['shared': 'higher'], 0))
        env.start()
        PropertySource lower = PropertySource.of('lower', ['shared': 'lower', 'lower.key': 'lower'], -10)
        Thread adder = null

        when: "the add waits for the lock of the catalogs while a refresh runs, and then a refresh that fails"
        env.holdingCatalogLock {
            adder = daemon('adder') { env.addPropertySource(lower) }
            awaitWaiting(adder)
            env.refresh()
            failing.armed.set(true)
            try {
                env.refresh()
            } catch (IllegalStateException ignored) {
                // expected
            }
        }
        adder.join(WAIT_SECONDS * 1000L)

        then: "the refresh that failed published nothing, so the first refresh's catalog stands"
        !adder.alive
        env.getProperty('shared', String).get() == 'higher'
        env.getProperty('lower.key', String).get() == 'lower'

        cleanup:
        env.stop()
    }

    private static ApplicationContextConfiguration withoutDefaultPropertySources() {
        ApplicationContext.builder()
                .environments('test')
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false) as ApplicationContextConfiguration
    }

    private static Thread parkIn(Parking parking, Closure<?> work) {
        parking.arm()
        Thread thread = daemon('refresher', work)
        assert parking.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        return thread
    }

    private static void release(Parking parking, Thread... threads) {
        parking.release.countDown()
        for (Thread thread : threads) {
            thread.join(WAIT_SECONDS * 1000L)
            assert !thread.alive
        }
    }

    private static void awaitWaiting(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (thread.alive && thread.state != Thread.State.BLOCKED) {
            assert System.nanoTime() < deadline: 'the adding thread never waited for the refresh'
            Thread.sleep(5)
        }
    }

    private static Thread daemon(String name, Closure<?> work) {
        Thread thread = new Thread(work, name)
        thread.daemon = true
        thread.start()
        return thread
    }

    /**
     * Parks, once armed, the thread that passes it.
     */
    static class Parking {
        final CountDownLatch reached = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final AtomicBoolean armed = new AtomicBoolean()

        void arm() {
            armed.set(true)
        }

        void pass() {
            if (armed.compareAndSet(true, false)) {
                reached.countDown()
                release.await(3 * AddBeforeRefreshReadsSpec.WAIT_SECONDS, TimeUnit.SECONDS)
            }
        }
    }

    /**
     * A property source read first that, once armed, fails the read.
     */
    static class FailingSource implements PropertySource {
        final AtomicBoolean armed = new AtomicBoolean()

        @Override
        String getName() { 'failing' }

        @Override
        int getOrder() { -100 }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                throw new IllegalStateException('failed to read')
            }
            return Collections.emptyIterator()
        }
    }

    /**
     * An environment without default property sources whose refresh parks, once armed, before it
     * stops the environment or once it has read the property sources, holding the lock of the
     * catalogs either way, and whose start, once armed, fails once it has read them.
     */
    static class ParkingEnvironment extends DefaultEnvironment {
        final Parking stopping = new Parking()
        final Parking started = new Parking()
        final AtomicBoolean failing = new AtomicBoolean()

        ParkingEnvironment() {
            super(AddBeforeRefreshReadsSpec.withoutDefaultPropertySources())
        }

        void holdingCatalogLock(Closure<?> work) {
            synchronized (catalog) {
                work.call()
            }
        }

        @Override
        Environment stop() {
            stopping.pass()
            return super.stop()
        }

        @Override
        Environment start() {
            Environment environment = super.start()
            started.pass()
            if (failing.compareAndSet(true, false)) {
                throw new IllegalStateException('failed once the property sources were read')
            }
            return environment
        }
    }
}

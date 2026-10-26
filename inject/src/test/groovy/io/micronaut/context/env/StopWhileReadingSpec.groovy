package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.core.order.Ordered
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

/**
 * {@code Environment#stop()} while another thread reads the property sources. The stop returns at
 * once and the environment is empty when it returns; the read in progress publishes and registers
 * nothing. The read is parked inside a property source while the stop runs, and every wait is
 * bounded.
 */
@Issue('https://github.com/micronaut-projects/micronaut-core/issues/14141')
@Timeout(60)
class StopWhileReadingSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "a stop during a refresh returns at once, and the refresh publishes and reports nothing"() {
        given:
        Map<String, Object> data = ['app.value': 'a']
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        env.addPropertySource(gate)
        env.start()
        Map<String, Object> diff = null
        Thread refresher = daemon('refresher') { diff = env.refreshAndDiff() }

        when: "the environment is stopped while the refresh is parked"
        data.put('app.value', 'b')
        gate.arm()
        refresher.start()
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread stopper = daemon('stopper') { env.stop() }
        stopper.start()
        stopper.join(WAIT_SECONDS * 1000L)
        boolean stoppedWhileParked = !stopper.alive
        boolean emptyWhileParked = !env.getProperty('app.value', String).present
        gate.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then: "the stop did not wait for the refresh"
        stoppedWhileParked
        emptyWhileParked

        and: "the refresh has published nothing and reports no change"
        !refresher.alive
        diff == [:]
        !env.running
        !env.getProperty('app.value', String).present

        when: "the environment is started again"
        env.start()

        then: "it reads the property sources again"
        env.getProperty('app.value', String).get() == 'b'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a stop from a thread that holds a lock a refresh needs does not deadlock with it"() {
        given: "a property source whose values need a lock of the application, processed after the gate"
        ReentrantLock appLock = new ReentrantLock()
        AtomicBoolean refreshGotLock = new AtomicBoolean()
        AtomicBoolean needLock = new AtomicBoolean()
        PropertySource needsLock = new PropertySource() {
            @Override
            String getName() { 'needs-lock' }

            @Override
            int getOrder() { Ordered.LOWEST_PRECEDENCE }

            @Override
            Object get(String key) { null }

            @Override
            Iterator<String> iterator() {
                if (needLock.get()) {
                    // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                    if (appLock.tryLock(StopWhileReadingSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                        refreshGotLock.set(true)
                        appLock.unlock()
                    }
                }
                return Collections.emptyIterator()
            }
        }
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a'], 0))
        env.addPropertySource(gate)
        env.addPropertySource(needsLock)
        env.start()
        Thread refresher = daemon('refresher') { env.refresh() }
        long[] stopMillis = [-1]
        Thread stopper = daemon('stopper') {
            appLock.lock()
            try {
                long start = System.nanoTime()
                env.stop()
                stopMillis[0] = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            } finally {
                appLock.unlock()
            }
        }

        when: "a thread that holds the lock stops the environment while the refresh is parked before that property source"
        needLock.set(true)
        gate.arm()
        refresher.start()
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        stopper.start()
        stopper.join(WAIT_SECONDS * 1000L)
        gate.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then: "the stop returns at once, and the refresh gets the lock after it"
        !stopper.alive
        !refresher.alive
        stopMillis[0] >= 0 && stopMillis[0] < 2000
        refreshGotLock.get()
        !env.running

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a stop while the environment starts leaves it empty, and the start publishes nothing after it"() {
        given: "a property source processed after 'data' that parks the start when it is asked for its convention"
        Later later = new Later()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a'], 0))
        env.addPropertySource(later)
        Thread starter = daemon('starter') { env.start() }

        when: "the environment is stopped once the start has published 'data'"
        later.armed.set(true)
        starter.start()
        assert later.parked.await(WAIT_SECONDS, TimeUnit.SECONDS)
        String seenDuringStart = env.getProperty('app.value', String).orElse(null)
        env.stop()
        later.release.countDown()
        starter.join(WAIT_SECONDS * 1000L)

        then: "the start published what it had read until then"
        seenDuringStart == 'a'

        and: "nothing is published after the stop"
        !starter.alive
        !env.running
        !env.getProperty('app.value', String).present
        !env.getProperty('later.key', String).present

        cleanup:
        later.release.countDown()
        env.stop()
    }

    private static ApplicationContextConfiguration withoutDefaultPropertySources() {
        ApplicationContext.builder()
                .environments('test')
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false) as ApplicationContextConfiguration
    }

    private static Thread daemon(String name, Closure<?> work) {
        Thread thread = new Thread(work, name)
        thread.daemon = true
        return thread
    }

    /**
     * A property source without properties, processed after 'data', that once armed parks the
     * thread that iterates it.
     */
    static class Gate implements PropertySource {
        final CountDownLatch entered = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final AtomicBoolean armed = new AtomicBoolean()

        void arm() {
            armed.set(true)
        }

        @Override
        String getName() { 'gate' }

        @Override
        int getOrder() { 10 }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                entered.countDown()
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                if (!release.await(StopWhileReadingSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the gate was never released')
                }
            }
            return Collections.emptyIterator()
        }
    }

    /**
     * A property source processed after 'data' that, once armed, parks the thread that asks for its
     * convention, which a read does before it processes it.
     */
    static class Later implements PropertySource {
        final AtomicBoolean armed = new AtomicBoolean()
        final CountDownLatch parked = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)

        @Override
        String getName() { 'later' }

        @Override
        int getOrder() { 10 }

        @Override
        Object get(String key) { key == 'later.key' ? 'later' : null }

        @Override
        Iterator<String> iterator() { ['later.key'].iterator() }

        @Override
        PropertyConvention getConvention() {
            if (armed.compareAndSet(true, false)) {
                parked.countDown()
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                if (!release.await(StopWhileReadingSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the start was never released')
                }
            }
            return PropertyConvention.JAVA_PROPERTIES
        }
    }
}

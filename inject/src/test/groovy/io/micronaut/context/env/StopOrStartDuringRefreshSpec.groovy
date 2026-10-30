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
 * A stop or a start from another thread while the environment refreshes does not wait for the
 * refresh. After a stop, the refresh publishes nothing and ends with the environment stopped; a
 * start returns at once, as the refresh starts the environment again. Of a stop and a start made
 * meanwhile, the later one counts. The threads are parked at fixed points, and every wait is
 * bounded.
 */
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/14141")
@Timeout(60)
class StopOrStartDuringRefreshSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "a stop from a thread that holds a lock the refresh needs returns at once, and the refresh publishes nothing and stops the environment: #how"() {
        given: "a refresh that parks before it stops the environment, and a property source that, once armed, needs a lock of the application"
        ReentrantLock appLock = new ReentrantLock()
        ParkingEnvironment env = new ParkingEnvironment()
        Map<String, Object> data = ['app.value': 'old']
        env.addPropertySource(new MapPropertySource('data', data))
        NeedsLock needsLock = new NeedsLock(appLock)
        env.addPropertySource(needsLock)
        env.start()
        Map<String, Object> diff = null

        when: "another thread takes the lock and stops the environment while the refresh is parked"
        data.put('app.value', 'new')
        data.put('new.key', 'new')
        needsLock.arm()
        Thread refresher = parkIn(env.stopping) {
            if (how == 'refreshAndDiff') {
                diff = env.refreshAndDiff()
            } else {
                env.refresh()
            }
        }
        Thread stopper = daemon('stopper') {
            appLock.lock()
            try {
                env.stop()
            } finally {
                appLock.unlock()
            }
        }
        stopper.join(WAIT_SECONDS * 1000L)
        boolean stopReturnedWhileParked = !stopper.alive
        release(env.stopping, refresher)

        then: "the stop returned while the refresh was parked, and the refresh got the lock"
        stopReturnedWhileParked
        needsLock.gotLock.get()

        and: "the refresh published nothing: the stopped environment answers from the raw catalog it had"
        !env.running
        env.getProperty('app.value', String).get() == 'old'
        !env.getProperty('new.key', String).isPresent()
        how == 'refresh' || diff == [:]

        cleanup:
        env.stopping.release.countDown()
        env.stop()

        where:
        how << ['refresh', 'refreshAndDiff']
    }

    void "a start from another thread returns at once while the refresh is parked #parked, and the refresh publishes what it read"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        Map<String, Object> data = ['app.value': 'old']
        env.addPropertySource(new MapPropertySource('data', data))
        Gate gate = new Gate()
        env.addPropertySource(gate)
        env.start()

        when:
        data.put('app.value', 'new')
        Thread refresher = parkIn(parked == 'before it stops the environment' ? env.stopping : gate) { env.refresh() }
        Thread starter = daemon('starter') { env.start() }
        starter.join(WAIT_SECONDS * 1000L)
        boolean startReturnedWhileParked = !starter.alive
        String valueWhileParked = env.getProperty('app.value', String).orElse(null)
        release(parked == 'before it stops the environment' ? env.stopping : gate, refresher)

        then: "the start returned while the refresh was parked, which then published what it read"
        startReturnedWhileParked
        valueWhileParked == 'old'
        env.running
        env.getProperty('app.value', String).get() == 'new'

        when: "the environment is refreshed again"
        data.put('app.value', 'newer')
        env.refresh()

        then: "the refresh does not wait for anything the start left behind"
        env.getProperty('app.value', String).get() == 'newer'

        cleanup:
        env.stopping.release.countDown()
        gate.release.countDown()
        env.stop()

        where:
        parked << ['before it stops the environment', 'while it reads the property sources']
    }

    void "of a #first and a #second from another thread while the refresh is parked, the later one counts"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        Map<String, Object> data = ['app.value': 'old']
        env.addPropertySource(new MapPropertySource('data', data))
        Gate gate = new Gate()
        env.addPropertySource(gate)
        env.start()

        when:
        data.put('app.value', 'new')
        Thread refresher = parkIn(gate) { env.refresh() }
        Thread caller = daemon('caller') {
            startOrStop(env, first)
            startOrStop(env, second)
        }
        caller.join(WAIT_SECONDS * 1000L)
        boolean returnedWhileParked = !caller.alive
        release(gate, refresher)

        then:
        returnedWhileParked
        env.running == (second == 'start')
        env.getProperty('app.value', String).get() == (second == 'start' ? 'new' : 'old')

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        first   | second
        'stop'  | 'start'
        'start' | 'stop'
    }

    void "stopping an application context while its environment refreshes on another thread does not wait for the refresh, which then stops the environment: bootstrap #bootstrap"() {
        given:
        ApplicationContext ctx = ApplicationContext.builder()
                .deduceEnvironment(false)
                .bootstrapEnvironment(bootstrap)
                .start()
        Environment env = ctx.environment
        Map<String, Object> data = ['app.value': 'old']
        env.addPropertySource(new MapPropertySource('data', data))
        Gate gate = new Gate()
        env.addPropertySource(gate)

        when: "the context is stopped while the refresh is parked in its read"
        data.put('app.value', 'new')
        Thread refresher = parkIn(gate) { env.refresh() }
        Thread stopper = daemon('stopper') { ctx.stop() }
        stopper.join(WAIT_SECONDS * 1000L)
        boolean stopReturnedWhileParked = !stopper.alive
        release(gate, refresher)

        then:
        stopReturnedWhileParked
        !ctx.running
        !env.running

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        bootstrap << [false, true]
    }

    private static void startOrStop(Environment env, String what) {
        if (what == 'stop') {
            env.stop()
        } else {
            env.start()
        }
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

    private static void release(Parking parking, Thread thread) {
        parking.release.countDown()
        thread.join(3 * WAIT_SECONDS * 1000L)
        assert !thread.alive
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
                // qualified: unqualified, Groovy resolves the name through get(String) of a property source
                release.await(3 * StopOrStartDuringRefreshSpec.WAIT_SECONDS, TimeUnit.SECONDS)
            }
        }
    }

    /**
     * A property source without properties, processed after the others, that once armed parks the
     * thread that iterates it.
     */
    static class Gate extends Parking implements PropertySource {
        @Override
        String getName() { 'gate' }

        @Override
        int getOrder() { Ordered.LOWEST_PRECEDENCE }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            pass()
            return Collections.emptyIterator()
        }
    }

    /**
     * A property source without properties that, once armed, waits for a lock of the application
     * when it is iterated.
     */
    static class NeedsLock implements PropertySource {
        final AtomicBoolean gotLock = new AtomicBoolean()
        private final ReentrantLock lock
        private final AtomicBoolean armed = new AtomicBoolean()

        NeedsLock(ReentrantLock lock) {
            this.lock = lock
        }

        void arm() {
            armed.set(true)
        }

        @Override
        String getName() { 'needs-lock' }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                if (lock.tryLock(StopOrStartDuringRefreshSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    gotLock.set(true)
                    lock.unlock()
                }
            }
            return Collections.emptyIterator()
        }
    }

    /**
     * An environment without default property sources whose refresh, once armed, parks before it
     * stops the environment, with the lock of the catalogs held.
     */
    static class ParkingEnvironment extends DefaultEnvironment {
        final Parking stopping = new Parking()

        ParkingEnvironment() {
            super(StopOrStartDuringRefreshSpec.withoutDefaultPropertySources())
        }

        @Override
        Environment stop() {
            stopping.pass()
            return super.stop()
        }
    }
}

package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.RefreshTestSupport.Gate
import io.micronaut.context.env.RefreshTestSupport.NeedsLock
import io.micronaut.context.env.RefreshTestSupport.Parking
import io.micronaut.context.env.RefreshTestSupport.ParkingEnvironment
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

import static io.micronaut.context.env.RefreshTestSupport.WAIT_SECONDS
import static io.micronaut.context.env.RefreshTestSupport.daemon
import static io.micronaut.context.env.RefreshTestSupport.parkIn
import static io.micronaut.context.env.RefreshTestSupport.release
import static io.micronaut.context.env.RefreshTestSupport.withoutDefaultPropertySources

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

    void "a thread that holds a lock which code run by stopping the environment needs, and stops it during the refresh, does not deadlock with the refresh that carries the stop out, when it then #how"() {
        given: "an environment whose resetCaches(), once armed, needs a lock of the application on the refreshing thread"
        ReentrantLock appLock = new ReentrantLock()
        ResetCachesNeedsLockEnvironment env = new ResetCachesNeedsLockEnvironment(appLock)
        env.addPropertySource(new MapPropertySource('data', ['app.value': 'old']))
        env.start()
        CountDownLatch stopped = new CountDownLatch(1)

        when: "another thread takes the lock and stops the environment once the refresh has read, and makes its next call while the refresh runs that code"
        Thread refresher = parkIn(env.started) { env.refresh() }
        Thread caller = daemon('caller') {
            appLock.lock()
            try {
                env.stop()
                stopped.countDown()
                if (env.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    nextCall.call(env)
                }
            } finally {
                appLock.unlock()
            }
        }
        assert stopped.await(WAIT_SECONDS, TimeUnit.SECONDS)
        env.arm()
        release(env.started, refresher)
        caller.join(WAIT_SECONDS * 1000L)

        then: "the call returned while that code waited for the lock, which it then got"
        !caller.alive
        env.gotLock.get()

        and: "the later call counts"
        env.running == running

        cleanup:
        env.started.release.countDown()
        env.stop()

        where:
        how                           | running | nextCall
        'starts the environment'      | true    | { Environment e -> e.start() }
        'stops the environment again' | false   | { Environment e -> e.stop() }
    }

    void "a start from another thread is not lost when code that the refresh runs has stopped the environment #when"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        Map<String, Object> data = ['app.value': 'old']
        env.addPropertySource(new MapPropertySource('data', data))
        StopsThenParks code = new StopsThenParks(env)
        Gate gate = new Gate()
        env.addPropertySource(gate)
        if (when == 'while the refresh reads') {
            env.addPropertySource(code)
        }
        env.start()

        when: "the code stops the environment and parks the refresh, and another thread then starts the environment"
        data.put('app.value', 'new')
        code.stopNext.set(true)
        Thread refresher
        if (when == 'while the refresh reads') {
            refresher = parkIn(code) { env.refresh() }
        } else {
            // parked once the refresh has read, so that the add is queued rather than only registered
            refresher = parkIn(env.started) { env.refresh() }
            code.arm()
            Thread adder = daemon('adder') { env.addPropertySource(code) }
            adder.join(WAIT_SECONDS * 1000L)
            env.started.release.countDown()
            assert code.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        }
        Thread starter = daemon('starter') { env.start() }
        starter.join(WAIT_SECONDS * 1000L)
        boolean startReturnedWhileParked = !starter.alive
        release(code, refresher)

        then: "the start returned while the refresh was parked, and the environment runs with what it read last"
        startReturnedWhileParked
        env.running
        env.getProperty('app.value', String).get() == 'new'
        env.getProperty('stopping.key', String).get() == 'stopping'

        cleanup:
        gate.release.countDown()
        env.started.release.countDown()
        code.release.countDown()
        env.stop()

        where:
        when << ['while the refresh reads', 'when the refresh processes it on top']
    }

    /**
     * A parking environment whose resetCaches(), once armed, needs the given lock the next time the
     * refreshing thread runs it.
     */
    static class ResetCachesNeedsLockEnvironment extends ParkingEnvironment {
        final CountDownLatch entered = new CountDownLatch(1)
        final AtomicBoolean gotLock = new AtomicBoolean()
        private final ReentrantLock lock
        private final AtomicBoolean armed = new AtomicBoolean()

        ResetCachesNeedsLockEnvironment(ReentrantLock lock) {
            this.lock = lock
        }

        void arm() {
            armed.set(true)
        }

        @Override
        protected void resetCaches() {
            super.resetCaches()
            // the name first: the fields are not set yet while the constructor runs
            if (Thread.currentThread().name == 'refresher' && armed.compareAndSet(true, false)) {
                entered.countDown()
                if (lock.tryLock(RefreshTestSupport.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    gotLock.set(true)
                    lock.unlock()
                }
            }
        }
    }

    /**
     * A property source that, once asked to, stops the environment the next time it is iterated,
     * and that, once armed, then parks the thread that iterates it.
     */
    static class StopsThenParks extends Parking implements PropertySource {
        final AtomicBoolean stopNext = new AtomicBoolean()
        private final Environment env

        StopsThenParks(Environment env) {
            this.env = env
        }

        @Override
        String getName() { 'stops-then-parks' }

        @Override
        Object get(String key) { key == 'stopping.key' ? 'stopping' : null }

        @Override
        Iterator<String> iterator() {
            if (stopNext.compareAndSet(true, false)) {
                env.stop()
            }
            pass()
            return ['stopping.key'].iterator()
        }
    }

    private static void startOrStop(Environment env, String what) {
        if (what == 'stop') {
            env.stop()
        } else {
            env.start()
        }
    }
}

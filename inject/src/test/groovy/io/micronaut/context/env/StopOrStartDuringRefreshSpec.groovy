package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.RefreshTestSupport.Gate
import io.micronaut.context.env.RefreshTestSupport.NeedsLock
import io.micronaut.context.env.RefreshTestSupport.ParkingEnvironment
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

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

    private static void startOrStop(Environment env, String what) {
        if (what == 'stop') {
            env.stop()
        } else {
            env.start()
        }
    }
}

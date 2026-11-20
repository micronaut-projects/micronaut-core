package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

@Issue('https://github.com/micronaut-projects/micronaut-core/issues/14214')
@Timeout(30)
class StopOrStartDuringStartSpec extends Specification {

    void 'a #call from another thread does not hold up a start that needs its lock with #kind environment'() {
        given:
        ReentrantLock appLock = new ReentrantLock()
        CountDownLatch parked = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        CountDownLatch callerHasLock = new CountDownLatch(1)
        AtomicBoolean startGotLock = new AtomicBoolean()
        ApplicationContext context = kind == 'context' ? ApplicationContext.builder()
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false)
                .start() : null
        DefaultEnvironment env = context == null
                ? new DefaultEnvironment((ApplicationContextConfiguration) ApplicationContext.builder()
                        .deduceEnvironment(false)
                        .enableDefaultPropertySources(false))
                : (DefaultEnvironment) context.environment
        if (context != null) {
            env.stop()
        }
        env.addPropertySource(new MapPropertySource('slow', [:]) {
            @Override
            int getOrder() { 0 }

            @Override
            Iterator<String> iterator() {
                parked.countDown()
                assert release.await(10, TimeUnit.SECONDS)
                return Collections.emptyIterator()
            }
        })
        env.addPropertySource(new MapPropertySource('needs-lock', ['app.value': 'ready']) {
            @Override
            int getOrder() { 10 }

            @Override
            Iterator<String> iterator() {
                if (appLock.tryLock(5, TimeUnit.SECONDS)) {
                    startGotLock.set(true)
                    appLock.unlock()
                }
                return super.iterator()
            }
        })

        when:
        Thread starter = new Thread({ env.start() }, 'starter')
        starter.start()
        assert parked.await(10, TimeUnit.SECONDS)
        Thread caller = new Thread({
            appLock.lock()
            try {
                callerHasLock.countDown()
                if (call == 'stop') {
                    env.stop()
                } else if (call == 'start') {
                    env.start()
                } else if (call == 'refresh') {
                    env.refresh()
                } else if (call == 'stopThenStart') {
                    env.stop()
                    env.start()
                } else if (call == 'startThenStop') {
                    env.start()
                    env.stop()
                }
            } finally {
                appLock.unlock()
            }
        }, 'caller')
        caller.start()
        assert callerHasLock.await(10, TimeUnit.SECONDS)
        caller.join(1000)
        boolean returnedWhileParked = !caller.alive
        boolean runningWhileParked = env.running
        release.countDown()
        starter.join(10_000)
        caller.join(10_000)

        then:
        !starter.alive
        !caller.alive
        returnedWhileParked
        runningWhileParked
        startGotLock.get()
        env.running == (call != 'stop' && call != 'startThenStop')
        !env.running || env.getProperty('app.value', String).get() == 'ready'

        cleanup:
        release.countDown()
        if (context == null) {
            env.stop()
        } else {
            context.stop()
        }

        where:
        kind      | call
        'plain'   | 'stop'
        'plain'   | 'start'
        'plain'   | 'refresh'
        'plain'   | 'stopThenStart'
        'plain'   | 'startThenStop'
        'context' | 'stop'
        'context' | 'start'
        'context' | 'refresh'
        'context' | 'stopThenStart'
        'context' | 'startThenStop'
    }
}

package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.ConfigurationException
import jakarta.inject.Singleton
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

@Issue('https://github.com/micronaut-projects/micronaut-core/issues/14214')
@Timeout(30)
class StopOrStartDuringStartSpec extends Specification {

    void 'a #call from another thread does not deadlock a start that needs its lock with #kind environment'() {
        given:
        ReentrantLock appLock = new ReentrantLock()
        CountDownLatch parked = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        CountDownLatch callerHasLock = new CountDownLatch(1)
        AtomicBoolean startGotLock = new AtomicBoolean()
        AtomicInteger needsLockReads = new AtomicInteger()
        AtomicReference<ConfigurationException> failure = new AtomicReference<>()
        AtomicReference<Throwable> callerFailure = new AtomicReference<>()
        AtomicReference<Throwable> starterFailure = new AtomicReference<>()
        ApplicationContext context = kind == 'runtime' ? ApplicationContext.builder()
                .deduceEnvironment(false)
                .build() : null
        DefaultEnvironment env = context == null
                ? new DefaultEnvironment((ApplicationContextConfiguration) ApplicationContext.builder()
                        .deduceEnvironment(false)
                        .enableDefaultPropertySources(false))
                : (DefaultEnvironment) context.environment
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
                needsLockReads.incrementAndGet()
                if (appLock.tryLock(5, TimeUnit.SECONDS)) {
                    startGotLock.set(true)
                    appLock.unlock()
                }
                return super.iterator()
            }
        })

        when:
        Thread starter = new Thread({
            try {
                env.start()
            } catch (Throwable e) {
                starterFailure.set(e)
            }
        }, 'starter')
        starter.start()
        assert parked.await(10, TimeUnit.SECONDS)
        Thread caller = new Thread({
            try {
                appLock.lock()
                try {
                    callerHasLock.countDown()
                    try {
                        if (call == 'stop') {
                            env.stop()
                        } else if (call == 'start') {
                            env.start()
                        } else if (call == 'refresh') {
                            env.refresh()
                        } else if (call == 'refreshAndDiff') {
                            env.refreshAndDiff()
                        } else if (call == 'stopThenStart') {
                            env.stop()
                            env.start()
                        } else if (call == 'startThenStop') {
                            env.start()
                        }
                    } catch (ConfigurationException e) {
                        failure.set(e)
                    }
                    if (call == 'startThenStop') {
                        env.stop()
                    }
                } finally {
                    appLock.unlock()
                }
            } catch (Throwable e) {
                callerFailure.set(e)
            }
        }, 'caller')
        caller.start()
        assert callerHasLock.await(10, TimeUnit.SECONDS)
        caller.join(5000)
        boolean returnedWhileParked = !caller.alive
        boolean runningWhileParked = env.running
        release.countDown()
        starter.join(10_000)
        caller.join(10_000)

        then:
        !starter.alive
        !caller.alive
        starterFailure.get() == null
        callerFailure.get() == null
        returnedWhileParked
        runningWhileParked
        startGotLock.get()
        needsLockReads.get() == 1
        (failure.get() == null) == (call == 'stop')
        failure.get() == null || failure.get().message.contains('Timed out waiting for environment start')
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
        'plain'   | 'refreshAndDiff'
        'plain'   | 'stopThenStart'
        'plain'   | 'startThenStop'
        'runtime' | 'stop'
        'runtime' | 'start'
        'runtime' | 'refresh'
        'runtime' | 'refreshAndDiff'
        'runtime' | 'stopThenStart'
        'runtime' | 'startThenStop'
    }

    void 'application context start waits for an environment start to publish configuration'() {
        given:
        ApplicationContext context = ApplicationContext.builder().deduceEnvironment(false).build()
        DefaultEnvironment env = (DefaultEnvironment) context.environment
        CountDownLatch parked = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        AtomicReference<Throwable> failure = new AtomicReference<>()
        AtomicReference<Throwable> environmentFailure = new AtomicReference<>()
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
        env.addPropertySource(new MapPropertySource('required-bean',
                ['spec.name': 'StopOrStartDuringStartSpec']))

        when:
        Thread environmentStarter = new Thread({
            try {
                env.start()
            } catch (Throwable e) {
                environmentFailure.set(e)
            }
        }, 'environment-starter')
        environmentStarter.start()
        assert parked.await(10, TimeUnit.SECONDS)
        Thread contextStarter = new Thread({
            try {
                context.start()
            } catch (Throwable e) {
                failure.set(e)
            }
        }, 'context-starter')
        contextStarter.start()
        contextStarter.join(200)
        boolean waitedForEnvironment = contextStarter.alive
        release.countDown()
        environmentStarter.join(10_000)
        contextStarter.join(10_000)

        then:
        waitedForEnvironment
        !environmentStarter.alive
        !contextStarter.alive
        environmentFailure.get() == null
        failure.get() == null
        context.containsBean(RequiredBean)

        cleanup:
        release.countDown()
        context.stop()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'StopOrStartDuringStartSpec')
    static class RequiredBean {
    }
}

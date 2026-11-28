package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.ConfigurationException
import jakarta.inject.Singleton
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

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
                .bootstrapEnvironment(false)
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
        env.running == !['stop', 'stopThenStart', 'startThenStop'].contains(call)
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
        ApplicationContext context = ApplicationContext.builder()
                .deduceEnvironment(false)
                .bootstrapEnvironment(false)
                .build()
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

    void 'a start waiting behind a deferred stop gets its turn and reads again'() {
        given:
        DefaultEnvironment env = new DefaultEnvironment((ApplicationContextConfiguration) ApplicationContext.builder()
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false))
        Map<String, Object> values = ['app.value': 'old']
        AtomicInteger reads = new AtomicInteger()
        AtomicBoolean firstGateRead = new AtomicBoolean(true)
        CountDownLatch parked = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        CountDownLatch stopReturned = new CountDownLatch(1)
        AtomicReference<Throwable> starterFailure = new AtomicReference<>()
        AtomicReference<Throwable> restarterFailure = new AtomicReference<>()
        env.addPropertySource(new MapPropertySource('changing', values) {
            @Override
            int getOrder() { 0 }

            @Override
            Iterator<String> iterator() {
                reads.incrementAndGet()
                return super.iterator()
            }
        })
        env.addPropertySource(new MapPropertySource('gate', [:]) {
            @Override
            int getOrder() { 10 }

            @Override
            Iterator<String> iterator() {
                if (firstGateRead.compareAndSet(true, false)) {
                    parked.countDown()
                    assert release.await(10, TimeUnit.SECONDS)
                }
                return Collections.emptyIterator()
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
        Thread restarter = new Thread({
            try {
                env.stop()
                values['app.value'] = 'new'
                stopReturned.countDown()
                env.start()
            } catch (Throwable e) {
                restarterFailure.set(e)
            }
        }, 'restarter')
        restarter.start()
        assert stopReturned.await(10, TimeUnit.SECONDS)
        release.countDown()
        starter.join(10_000)
        restarter.join(10_000)

        then:
        !starter.alive
        !restarter.alive
        starterFailure.get() == null
        restarterFailure.get() == null
        reads.get() == 2
        env.running
        env.getProperty('app.value', String).get() == 'new'

        cleanup:
        release.countDown()
        env.stop()
    }

    void 'a start on a running environment does not wait for an in-flight refresh'() {
        given:
        DefaultEnvironment env = new DefaultEnvironment((ApplicationContextConfiguration) ApplicationContext.builder()
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false))
        AtomicBoolean block = new AtomicBoolean()
        CountDownLatch parked = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        AtomicReference<Throwable> refreshFailure = new AtomicReference<>()
        AtomicReference<Throwable> startFailure = new AtomicReference<>()
        env.addPropertySource(new MapPropertySource('blocking-refresh', [:]) {
            @Override
            Iterator<String> iterator() {
                if (block.get()) {
                    parked.countDown()
                    assert release.await(10, TimeUnit.SECONDS)
                }
                return Collections.emptyIterator()
            }
        })
        env.start()

        when:
        block.set(true)
        Thread refresher = new Thread({
            try {
                env.refresh()
            } catch (Throwable e) {
                refreshFailure.set(e)
            }
        }, 'refresher')
        refresher.start()
        assert parked.await(10, TimeUnit.SECONDS)
        Thread starter = new Thread({
            try {
                env.start()
            } catch (Throwable e) {
                startFailure.set(e)
            }
        }, 'starter')
        starter.start()
        starter.join(500)
        boolean returnedDuringRefresh = !starter.alive
        release.countDown()
        refresher.join(10_000)
        starter.join(10_000)

        then:
        returnedDuringRefresh
        !refresher.alive
        !starter.alive
        refreshFailure.get() == null
        startFailure.get() == null
        env.running

        cleanup:
        release.countDown()
        env.stop()
    }

    void 'a stop requested after a waiting start remains the final operation'() {
        given:
        DefaultEnvironment env = new DefaultEnvironment((ApplicationContextConfiguration) ApplicationContext.builder()
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false))
        AtomicInteger reads = new AtomicInteger()
        AtomicBoolean firstGateRead = new AtomicBoolean(true)
        CountDownLatch parked = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        AtomicReference<Throwable> starterFailure = new AtomicReference<>()
        AtomicReference<Throwable> waiterFailure = new AtomicReference<>()
        env.addPropertySource(new MapPropertySource('counted', [:]) {
            @Override
            int getOrder() { 0 }

            @Override
            Iterator<String> iterator() {
                reads.incrementAndGet()
                return Collections.emptyIterator()
            }
        })
        env.addPropertySource(new MapPropertySource('gate', [:]) {
            @Override
            int getOrder() { 10 }

            @Override
            Iterator<String> iterator() {
                if (firstGateRead.compareAndSet(true, false)) {
                    parked.countDown()
                    assert release.await(10, TimeUnit.SECONDS)
                }
                return Collections.emptyIterator()
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
        Thread waiter = new Thread({
            try {
                env.start()
            } catch (Throwable e) {
                waiterFailure.set(e)
            }
        }, 'waiter')
        waiter.start()
        new PollingConditions(timeout: 10).eventually {
            assert waiter.state == Thread.State.TIMED_WAITING
        }
        env.stop()
        release.countDown()
        starter.join(10_000)
        waiter.join(10_000)

        then:
        !starter.alive
        !waiter.alive
        starterFailure.get() == null
        waiterFailure.get() == null
        reads.get() == 1
        !env.running

        cleanup:
        release.countDown()
        env.stop()
    }

    void 'a deferred stop runs the operation without invoking the public override twice'() {
        given:
        CountingStopEnvironment env = new CountingStopEnvironment(
                (ApplicationContextConfiguration) ApplicationContext.builder()
                        .deduceEnvironment(false)
                        .enableDefaultPropertySources(false))
        CountDownLatch parked = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        AtomicReference<Throwable> starterFailure = new AtomicReference<>()
        env.addPropertySource(new MapPropertySource('gate', [:]) {
            @Override
            Iterator<String> iterator() {
                parked.countDown()
                assert release.await(10, TimeUnit.SECONDS)
                return Collections.emptyIterator()
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
        env.stop()
        release.countDown()
        starter.join(10_000)

        then:
        !starter.alive
        starterFailure.get() == null
        env.stopCalls.get() == 1
        !env.running

        cleanup:
        release.countDown()
    }

    void 'a property source removed after a deferred stop is not registered again'() {
        given:
        DefaultEnvironment env = new DefaultEnvironment((ApplicationContextConfiguration) ApplicationContext.builder()
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false))
        CountDownLatch parked = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        AtomicInteger removedSourceReads = new AtomicInteger()
        AtomicReference<Throwable> starterFailure = new AtomicReference<>()
        env.addPropertySource(new MapPropertySource('gate', [:]) {
            @Override
            int getOrder() { 0 }

            @Override
            Iterator<String> iterator() {
                parked.countDown()
                assert release.await(10, TimeUnit.SECONDS)
                return Collections.emptyIterator()
            }
        })
        PropertySource removed = new MapPropertySource('removed', ['removed.value': 'present']) {
            @Override
            int getOrder() { 10 }

            @Override
            Iterator<String> iterator() {
                removedSourceReads.incrementAndGet()
                return super.iterator()
            }
        }
        env.addPropertySource(removed)

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
        env.stop()
        env.removePropertySource(removed)
        release.countDown()
        starter.join(10_000)
        env.start()

        then:
        !starter.alive
        starterFailure.get() == null
        removedSourceReads.get() == 0
        !env.propertySources.containsValue(removed)
        !env.getProperty('removed.value', String).present

        cleanup:
        release.countDown()
        env.stop()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'StopOrStartDuringStartSpec')
    static class RequiredBean {
    }

    private static final class CountingStopEnvironment extends DefaultEnvironment {
        final AtomicInteger stopCalls = new AtomicInteger()

        CountingStopEnvironment(ApplicationContextConfiguration configuration) {
            super(configuration)
        }

        @Override
        Environment stop() {
            stopCalls.incrementAndGet()
            return super.stop()
        }
    }
}

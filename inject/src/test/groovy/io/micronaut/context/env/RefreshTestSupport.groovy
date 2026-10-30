package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.core.order.Ordered

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

/**
 * What the specs of calls from other threads during a refresh share: threads parked at fixed points,
 * property sources that park or need a lock, and bounded waits.
 */
class RefreshTestSupport {

    static final int WAIT_SECONDS = 10

    /**
     * The configuration of an environment that holds the property sources a test gives it and
     * nothing of the machine it runs on.
     */
    static ApplicationContextConfiguration withoutDefaultPropertySources() {
        ApplicationContext.builder()
                .environments('test')
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false) as ApplicationContextConfiguration
    }

    /**
     * Runs the work on another thread, once the parking is armed, and returns that thread once it is
     * parked.
     */
    static Thread parkIn(Parking parking, Closure<?> work) {
        parking.arm()
        Thread thread = daemon('refresher', work)
        assert parking.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        return thread
    }

    /**
     * Lets the parked thread go and waits for it to end.
     */
    static void release(Parking parking, Thread thread) {
        parking.release.countDown()
        thread.join(3 * WAIT_SECONDS * 1000L)
        assert !thread.alive
    }

    static Thread daemon(String name, Closure<?> work) {
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
                release.await(3 * RefreshTestSupport.WAIT_SECONDS, TimeUnit.SECONDS)
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
     * A property source without properties, processed after the others, that once armed waits for
     * the given lock when it is iterated.
     */
    static class NeedsLock implements PropertySource {
        final CountDownLatch entered = new CountDownLatch(1)
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
        int getOrder() { Ordered.LOWEST_PRECEDENCE }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                entered.countDown()
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                if (lock.tryLock(RefreshTestSupport.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    gotLock.set(true)
                    lock.unlock()
                }
            }
            return Collections.emptyIterator()
        }
    }

    /**
     * An environment without default property sources whose refresh, once armed, parks before it
     * stops the environment or once it has read the property sources, with the lock of the catalogs
     * held either way, and whose start, once armed, fails once it has read them.
     */
    static class ParkingEnvironment extends DefaultEnvironment {
        final Parking stopping = new Parking()
        final Parking started = new Parking()
        final AtomicBoolean failing = new AtomicBoolean()

        ParkingEnvironment() {
            super(RefreshTestSupport.withoutDefaultPropertySources())
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

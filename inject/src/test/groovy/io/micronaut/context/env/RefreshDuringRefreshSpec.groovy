package io.micronaut.context.env

import io.micronaut.context.env.RefreshTestSupport.Gate
import io.micronaut.context.env.RefreshTestSupport.NeedsLock
import org.slf4j.Logger
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

import static io.micronaut.context.env.RefreshTestSupport.WAIT_SECONDS
import static io.micronaut.context.env.RefreshTestSupport.daemon
import static io.micronaut.context.env.RefreshTestSupport.parkIn
import static io.micronaut.context.env.RefreshTestSupport.release
import static io.micronaut.context.env.RefreshTestSupport.withoutDefaultPropertySources

/**
 * A refresh from another thread while the environment refreshes waits for that refresh at most two
 * seconds. If it ends in time, the second refresh reads the property sources itself; otherwise the
 * second returns with the properties as they are, and the refresh in progress reads them once more
 * before it ends. The threads are parked at fixed points, and every wait is bounded.
 */
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/14160")
@Timeout(60)
class RefreshDuringRefreshSpec extends Specification {

    void "a #method from a thread that holds a lock the refresh in progress needs returns after about two seconds without its change, and the refresh in progress then reads once more and applies it"() {
        given: "a property source, read after 'data', that once armed waits for a lock of the application"
        Map<String, Object> data = ['app.value': 'a']
        ReentrantLock appLock = new ReentrantLock()
        NeedsLock needsLock = new NeedsLock(appLock)
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        Logger logger = Mock(Logger)
        env.log = logger
        env.addPropertySource(new MapPropertySource('data', data))
        env.addPropertySource(needsLock)
        env.start()
        CountDownLatch locked = new CountDownLatch(1)
        CountDownLatch go = new CountDownLatch(1)
        Thread first = null
        boolean firstWaitedWhenSecondReturned = false
        long waitedMillis = -1
        Map<String, Object> diff = null
        Throwable failure = null

        when: "the first refresh waits for the lock that the thread of the second holds, and the second changes a value and refreshes"
        Thread second = daemon('second-refresh') {
            appLock.lock()
            try {
                locked.countDown()
                if (go.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    data.put('app.value', 'b')
                    long start = System.nanoTime()
                    if (method == 'refreshAndDiff') {
                        diff = env.refreshAndDiff()
                    } else {
                        env.refresh()
                    }
                    waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                    firstWaitedWhenSecondReturned = first.alive
                }
            } catch (Throwable t) {
                failure = t
            } finally {
                appLock.unlock()
            }
        }
        assert locked.await(WAIT_SECONDS, TimeUnit.SECONDS)
        needsLock.arm()
        first = daemon('first-refresh') { env.refresh() }
        assert needsLock.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        go.countDown()
        second.join(WAIT_SECONDS * 1000L)
        first.join(WAIT_SECONDS * 1000L)

        then: "the second returned after waiting about two seconds, while the first still waited for the lock"
        !second.alive
        failure == null
        waitedMillis >= 1000 && waitedMillis < 5000
        firstWaitedWhenSecondReturned

        and: "the second reports no change, as it has published none"
        diff == reported

        and: "the first got the lock once the second let go of it, and read once more: the change is applied"
        !first.alive
        needsLock.gotLock.get()
        env.getProperty('app.value', String).get() == 'b'

        and: "no refresh is logged as not carried out"
        0 * logger.warn(*_)

        cleanup:
        go.countDown()
        env.stop()

        where:
        method           | reported
        'refresh'        | null
        'refreshAndDiff' | [:]
    }

    void "a refreshAndDiff that reads once more for a refresh that could not wait for it reports its own change and that of the read made once more, and the refresh that asked reports none"() {
        given: "a refresh that can be parked while it reads the values"
        Map<String, Object> data = ['first.value': 'a', 'second.value': 'x']
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        env.addPropertySource(gate)
        env.start()
        Map<String, Object> firstDiff = null
        Map<String, Object> secondDiff = null

        when: "a value changes, a refreshAndDiff reads it and is parked, another value changes, and a second refreshAndDiff gives up waiting"
        data.put('first.value', 'b')
        Thread first = parkIn(gate) { firstDiff = env.refreshAndDiff() }
        data.put('second.value', 'y')
        Thread second = daemon('second-refresh') { secondDiff = env.refreshAndDiff() }
        second.join(WAIT_SECONDS * 1000L)
        boolean secondReturnedWhileParked = !second.alive
        String secondValueWhileParked = env.getProperty('second.value', String).orElse(null)
        release(gate, first)

        then: "the second returned while the first was parked, without its change, and reports none"
        secondReturnedWhileParked
        secondValueWhileParked == 'x'
        secondDiff == [:]

        and: "the first read once more, and reports both changes, each with the value it had before"
        firstDiff == ['first.value': 'a', 'second.value': 'x']
        env.getProperty('first.value', String).get() == 'b'
        env.getProperty('second.value', String).get() == 'y'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a refresh from an interrupted thread while another refresh is parked returns at once and keeps its interrupt, and the refresh in progress reads once more"() {
        given:
        Map<String, Object> data = ['app.value': 'a']
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        env.addPropertySource(gate)
        env.start()
        boolean interruptKept = false
        long waitedMillis = -1

        when:
        Thread first = parkIn(gate) { env.refresh() }
        data.put('app.value', 'b')
        Thread second = daemon('second-refresh') {
            Thread.currentThread().interrupt()
            long start = System.nanoTime()
            env.refresh()
            waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            interruptKept = Thread.currentThread().isInterrupted()
        }
        second.join(WAIT_SECONDS * 1000L)
        boolean secondReturnedWhileParked = !second.alive
        release(gate, first)

        then:
        secondReturnedWhileParked
        waitedMillis >= 0 && waitedMillis < 1000
        interruptKept
        env.getProperty('app.value', String).get() == 'b'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source whose code refreshes the environment on another thread, and waits for that thread, does not deadlock with the refresh that reads it"() {
        given:
        Map<String, Object> data = ['app.value': 'a']
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        RefreshesOnAnotherThread refreshing = new RefreshesOnAnotherThread(env)
        env.addPropertySource(refreshing)
        env.start()

        when:
        data.put('app.value', 'b')
        refreshing.arm()
        long refreshMillis = -1
        Thread refresher = daemon('refresher') {
            long start = System.nanoTime()
            env.refresh()
            refreshMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        }
        refresher.join(3 * WAIT_SECONDS * 1000L)

        then: "the refresh on the other thread returned after about two seconds without its read, and the refresh read once more"
        !refresher.alive
        refreshing.otherReturned.get()
        refreshMillis >= 1000 && refreshMillis < 5000
        env.getProperty('app.value', String).get() == 'b'

        cleanup:
        env.stop()
    }

    void "a property source whose code refreshes the environment on another thread every time it is read makes the refresh read once more, and no more, and the refresh not carried out is logged"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        Logger logger = Mock(Logger)
        env.log = logger
        RefreshesOnAnotherThread refreshing = new RefreshesOnAnotherThread(env)
        env.addPropertySource(refreshing)
        env.start()

        when:
        refreshing.everyTime = true
        refreshing.reads.set(0)
        Thread refresher = daemon('refresher') { env.refresh() }
        refresher.join(3 * WAIT_SECONDS * 1000L)
        refreshing.everyTime = false

        then: "it was read by the refresh and once more, and each refresh on the other thread returned"
        !refresher.alive
        refreshing.reads.get() == 2
        refreshing.otherReturned.get()

        and: "the refresh that asked after the read made once more is logged as not carried out"
        1 * logger.warn({ String message -> message.contains('was not carried out') })

        cleanup:
        refreshing.everyTime = false
        env.stop()
    }

    void "a read made once more that fails is logged, and the refresh that made it keeps what it published and does not fail"() {
        given:
        Map<String, Object> data = ['app.value': 'a']
        Gate gate = new Gate()
        FailsWhenArmed failing = new FailsWhenArmed()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        env.addPropertySource(failing)
        env.addPropertySource(gate)
        env.start()
        Throwable firstFailure = null

        when: "a second refresh gives up waiting for the parked one, and the property source then fails"
        data.put('app.value', 'b')
        Thread first = parkIn(gate) {
            try {
                env.refresh()
            } catch (Throwable t) {
                firstFailure = t
            }
        }
        Thread second = daemon('second-refresh') { env.refresh() }
        second.join(WAIT_SECONDS * 1000L)
        boolean secondReturnedWhileParked = !second.alive
        failing.armed.set(true)
        release(gate, first)

        then:
        secondReturnedWhileParked
        firstFailure == null
        failing.failed.get()
        env.getProperty('app.value', String).get() == 'b'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    /**
     * A property source without properties that, once armed or every time while asked to, refreshes
     * the environment on another thread when it is iterated and waits a bounded time for that
     * thread.
     */
    static class RefreshesOnAnotherThread implements PropertySource {
        final AtomicBoolean otherReturned = new AtomicBoolean()
        final AtomicInteger reads = new AtomicInteger()
        volatile boolean everyTime
        private final Environment env
        private final AtomicBoolean armed = new AtomicBoolean()

        RefreshesOnAnotherThread(Environment env) {
            this.env = env
        }

        void arm() {
            armed.set(true)
        }

        @Override
        String getName() { 'refreshes-on-another-thread' }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            reads.incrementAndGet()
            if (armed.compareAndSet(true, false) || everyTime) {
                Thread other = RefreshTestSupport.daemon('other-refresh') {
                    env.refresh()
                    otherReturned.set(true)
                }
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                other.join(RefreshTestSupport.WAIT_SECONDS * 1000L)
            }
            return Collections.emptyIterator()
        }
    }

    /**
     * A property source without properties that, once armed, fails the next time it is iterated.
     */
    static class FailsWhenArmed implements PropertySource {
        final AtomicBoolean armed = new AtomicBoolean()
        final AtomicBoolean failed = new AtomicBoolean()

        @Override
        String getName() { 'fails-when-armed' }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                failed.set(true)
                throw new IllegalStateException('failed to read')
            }
            return Collections.emptyIterator()
        }
    }
}

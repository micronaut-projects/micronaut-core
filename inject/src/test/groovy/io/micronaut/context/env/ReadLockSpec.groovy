package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.core.order.Ordered
import spock.lang.Specification
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

/**
 * One read of the property sources at a time. A start or a refresh waits at most two seconds for the
 * read in progress; if that does not end in time, it asks it to read once more and returns, and the
 * read in progress reads again before it ends. A refresh from code that runs in the read, on its
 * thread, asks for that once per read at most. The threads are parked at fixed points, and every
 * wait is bounded.
 */
@Timeout(60)
class ReadLockSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "a property source that refreshes the environment every time it is read makes a #read read once more, and no more, leaving nothing out, and the extra read publishes"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        RefreshesWhenRead refreshing = new RefreshesWhenRead(env)
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(refreshing)
        env.addPropertySource(PropertySource.of('after', ['after.key': 'after']))
        if (read == 'refresh') {
            env.start()
            refreshing.reads.set(0)
        }
        Throwable failure = null

        when:
        Thread reader = daemon('reader') {
            try {
                read == 'refresh' ? env.refresh() : env.start()
            } catch (Throwable t) {
                failure = t
            }
        }
        reader.join(WAIT_SECONDS * 1000L)

        then: "it was read twice: by the read, and by the one more read that its refreshes asked for"
        !reader.alive
        failure == null
        refreshing.reads.get() == 2

        and: "nothing is left out, and what the extra read has read is published"
        env.getProperty('refreshing.reads', Integer).get() == 2
        env.getProperty('app.value', String).get() == 'a'
        env.getProperty('after.key', String).get() == 'after'

        cleanup:
        env.stop()

        where:
        read << ['start', 'refresh']
    }

    void "a refresh from a thread that holds a lock the refresh in progress needs returns after about two seconds without its change, and the refresh in progress then reads again and applies it: #method"() {
        given: "a property source, read after 'data', that once armed waits for a lock of the application"
        Map<String, Object> data = ['app.value': 'a']
        ReentrantLock appLock = new ReentrantLock()
        NeedsLock needsLock = new NeedsLock(appLock)
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
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

        and: "the first got the lock once the second released it, and read again: the change is applied"
        !first.alive
        needsLock.gotLock.get()
        env.getProperty('app.value', String).get() == 'b'

        cleanup:
        go.countDown()
        env.stop()

        where:
        method           | reported
        'refresh'        | null
        'refreshAndDiff' | [:]
    }

    void "a refreshAndDiff that reads again for a refresh that could not wait for it reports its own change and that of the extra read, and the refresh that asked for it reports none"() {
        given: "a refresh that can be parked once it has read the values, before it publishes them"
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

        and: "the first read again, and reports both changes, each with the value it had before"
        firstDiff == ['first.value': 'a', 'second.value': 'x']
        env.getProperty('first.value', String).get() == 'b'
        env.getProperty('second.value', String).get() == 'y'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source whose code refreshes the environment, added while the environment reads, is visible at once, and the read reads once more before it ends: #read"() {
        given:
        Gate gate = new Gate()
        Map<String, Object> data = ['app.value': 'a']
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        env.addPropertySource(gate)
        if (read == 'refresh') {
            env.start()
        }
        RefreshesWhenIterated refreshing = new RefreshesWhenIterated(env)

        when: "it is added while the read is parked, and the add reads it, which refreshes the environment"
        Thread reader = parkIn(gate) { read == 'refresh' ? env.refresh() : env.start() }
        data.put('app.value', 'b')
        changeWhileParked {
            env.addPropertySource(refreshing)
        }
        String addedWhileParked = env.getProperty('refreshing.key', String).orElse(null)
        String valueWhileParked = env.getProperty('app.value', String).orElse(null)
        release(gate, reader)

        then: "the add returned while the read was parked, its refresh having waited for the read at most two seconds, and it was visible at once"
        addedWhileParked == 'refreshing'
        valueWhileParked == 'a'

        and: "the read reads once more before it ends, for the refresh the add has run"
        env.getProperty('app.value', String).get() == 'b'
        env.getProperty('refreshing.key', String).get() == 'refreshing'
        env.propertySources*.name.contains('refreshing')

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        read << ['start', 'refresh']
    }

    void "a property source added and the environment refreshed by another thread while the start is parked: the add is visible at once, the refresh returns once it has waited two seconds, and the start reads once more: #how"() {
        given:
        Gate gate = new Gate()
        Map<String, Object> data = ['app.value': 'a']
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        env.addPropertySource(gate)
        Throwable failure = null
        Map<String, Object> diff = null

        when: "the start is parked after it has taken the registered property sources"
        Thread reader = parkIn(gate) { env.start() }

        and: "another thread adds a property source and refreshes the environment"
        data.put('app.value', 'b')
        Thread other = daemon('other') {
            try {
                env.addPropertySource(PropertySource.of('x', ['x.key': 'x']))
                if (how == 'refresh') {
                    env.refresh()
                } else {
                    diff = env.refreshAndDiff()
                }
            } catch (Throwable t) {
                failure = t
            }
        }
        new PollingConditions(timeout: WAIT_SECONDS).eventually {
            // waits, at most two seconds, for the read lock the start holds
            assert other.state == Thread.State.TIMED_WAITING
        }
        other.join(WAIT_SECONDS * 1000L)
        boolean otherDoneWhileParked = !other.alive
        String addedWhileParked = env.getProperty('x.key', String).orElse(null)
        String valueWhileParked = env.getProperty('app.value', String).orElse(null)
        release(gate, reader)

        then: "the other thread returned while the start was parked, its refresh not read yet"
        otherDoneWhileParked
        failure == null
        how == 'refresh' || diff == [:]
        addedWhileParked == 'x'
        valueWhileParked == 'a'

        and: "the start has read the property sources once more, with what that thread added"
        env.getProperty('app.value', String).get() == 'b'
        env.getProperty('x.key', String).get() == 'x'
        env.propertySources*.name.contains('x')

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        how << ['refresh', 'refreshAndDiff']
    }

    /**
     * The configuration of an environment that holds the property sources a test gives it and
     * nothing of the machine it runs on.
     */
    private static ApplicationContextConfiguration withoutDefaultPropertySources() {
        ApplicationContext.builder()
                .environments('test')
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false) as ApplicationContextConfiguration
    }

    /**
     * Runs the read on another thread, once the parking is armed, and returns that thread once the
     * read is parked.
     */
    private static Thread parkIn(Parking parking, Closure<?> read) {
        parking.arm()
        Thread reader = daemon('reader', read)
        assert parking.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        return reader
    }

    /**
     * Runs the change on another thread, which must finish while the read is parked.
     */
    private static void changeWhileParked(Closure<?> change) {
        Thread changer = daemon('changer', change)
        changer.join(WAIT_SECONDS * 1000L)
        assert !changer.alive
    }

    private static void release(Parking parking, Thread reader) {
        parking.release.countDown()
        reader.join(WAIT_SECONDS * 1000L)
        assert !reader.alive
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

        boolean pass() {
            if (armed.compareAndSet(true, false)) {
                reached.countDown()
                // qualified: unqualified, Groovy resolves the name through get(String) of a property source
                if (!release.await(ReadLockSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the parked thread was never released')
                }
                return true
            }
            return false
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
     * A property source that refreshes the environment every time it is iterated, and gives how
     * often it has been iterated.
     */
    static class RefreshesWhenRead implements PropertySource {
        final AtomicInteger reads = new AtomicInteger()
        private final Environment env

        RefreshesWhenRead(Environment env) {
            this.env = env
        }

        @Override
        String getName() { 'refreshing' }

        @Override
        Object get(String key) { key == 'refreshing.reads' ? reads.get() : null }

        @Override
        Iterator<String> iterator() {
            reads.incrementAndGet()
            env.refresh()
            return ['refreshing.reads'].iterator()
        }
    }

    /**
     * A property source that refreshes the environment the first time it is iterated.
     */
    static class RefreshesWhenIterated implements PropertySource {
        private final Environment env
        private final AtomicBoolean iterated = new AtomicBoolean()

        RefreshesWhenIterated(Environment env) {
            this.env = env
        }

        @Override
        String getName() { 'refreshing' }

        @Override
        Object get(String key) { key == 'refreshing.key' ? 'refreshing' : null }

        @Override
        Iterator<String> iterator() {
            if (iterated.compareAndSet(false, true)) {
                env.refresh()
            }
            return ['refreshing.key'].iterator()
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
                if (lock.tryLock(ReadLockSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    gotLock.set(true)
                    lock.unlock()
                }
            }
            return Collections.emptyIterator()
        }
    }
}

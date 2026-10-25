package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.core.order.Ordered
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

/**
 * A property source added to a running environment by another thread just before or while a
 * refresh starts, or once it has read the property sources: the thread that adds it does not wait
 * for the refresh, and it is processed once, in its order if the refresh reads it. The refresh is
 * parked where it stops the environment, after it has taken the lock of the catalogs and before it
 * reads the property sources, or where it compares what it has read, and the threads are
 * sequenced with latches. Every wait is bounded.
 */
@Timeout(90)
class AddPropertySourceDuringRefreshSpec extends Specification {

    private static final int WAIT_SECONDS = 10
    /**
     * How long a parked thread waits to be released: longer than the waits that tell whether a
     * thread waits for it, so that a thread that does is not let go while it is waited for.
     */
    private static final int PARK_SECONDS = 3 * WAIT_SECONDS

    void "a property source added while a refresh starts does not make the thread that adds it wait for the refresh, and is processed once, in its order: #how"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(PropertySource.of('higher', ['order.value': 'higher'], 100))
        env.start()

        when: "a property source of a lower order is added while the refresh is parked before it reads"
        Thread refresher = parkIn(env.stopping) { refresh.call(env) }
        Thread adder = daemon('adder') {
            env.addPropertySource(PropertySource.of('lower', ['order.value': 'lower', 'lower.key': 'lower'], -100))
        }
        adder.join(WAIT_SECONDS * 1000L)
        boolean adderWaited = adder.alive
        release(env.stopping, refresher)
        adder.join(WAIT_SECONDS * 1000L)

        then: "the thread that adds it does not wait, and the property source of the higher order keeps the key"
        verifyAll {
            !adderWaited
            !adder.alive
            env.getProperty('lower.key', String).orElse(null) == 'lower'
            env.getProperty('order.value', String).orElse(null) == 'higher'
        }

        cleanup:
        env.stopping.release.countDown()
        env.stop()

        where:
        how              | refresh
        'refresh'        | { DefaultEnvironment e -> e.refresh() }
        'refreshAndDiff' | { DefaultEnvironment e -> e.refreshAndDiff() }
    }

    void "a thread that adds a property source while a refresh starts, holding a lock that the refresh needs, does not deadlock with it: #how"() {
        given:
        ReentrantLock lock = new ReentrantLock()
        NeedsLock needsLock = new NeedsLock(lock)
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(needsLock)
        env.start()

        when: "the thread holds the lock while it adds the property source, and the refresh, once released, reads the one that needs the lock"
        needsLock.armed.set(true)
        Thread refresher = parkIn(env.stopping) { refresh.call(env) }
        Thread adder = daemon('adder') {
            lock.lock()
            try {
                env.addPropertySource(PropertySource.of('added', ['added.key': 'added']))
            } finally {
                lock.unlock()
            }
        }
        awaitDoneOrBlocked(adder)
        release(env.stopping, refresher)
        adder.join(WAIT_SECONDS * 1000L)

        then: "the refresh gets the lock"
        !needsLock.timedOut
        !adder.alive
        env.getProperty('added.key', String).orElse(null) == 'added'

        cleanup:
        env.stopping.release.countDown()
        env.stop()

        where:
        how              | refresh
        'refresh'        | { DefaultEnvironment e -> e.refresh() }
        'refreshAndDiff' | { DefaultEnvironment e -> e.refreshAndDiff() }
    }

    void "a refresh that starts while another thread processes a property source it has added reads the property sources once it has been processed"() {
        given:
        List<String> events = Collections.synchronizedList([])
        Noting noting = new Noting(events)
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('higher', ['order.value': 'higher'], 100))
        env.addPropertySource(noting)
        env.start()
        ParksWhileProcessed lower = new ParksWhileProcessed(events)

        when: "a refresh starts while the property source is being processed"
        lower.arm()
        noting.armed.set(true)
        Thread adder = daemon('adder') { env.addPropertySource(lower) }
        assert lower.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread refresher = daemon('refresher') { env.refresh() }
        awaitWaiting(refresher)
        lower.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        refresher.join(WAIT_SECONDS * 1000L)

        then: "both finish, and the refresh has read the property sources after the property source was processed"
        !adder.alive
        !refresher.alive
        events == ['processed', 'read']
        env.getProperty('order.value', String).get() == 'higher'
        env.getProperty('lower.key', String).get() == 'lower'

        cleanup:
        lower.release.countDown()
        env.stop()
    }

    void "a property source added while a refresh waits for a property source another thread processes is left to that refresh, without waiting for it, and is processed in its order"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('higher', ['order.value': 'higher'], 100))
        env.start()
        ParksWhileProcessed processed = new ParksWhileProcessed(Collections.synchronizedList([]))

        when: "a refresh waits for a property source another thread is processing, and a third thread adds one meanwhile"
        processed.arm()
        Thread adder = daemon('adder') { env.addPropertySource(processed) }
        assert processed.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread refresher = daemon('refresher') { env.refresh() }
        awaitWaiting(refresher)
        Thread third = daemon('third') {
            env.addPropertySource(PropertySource.of('announced', ['order.value': 'announced', 'announced.key': 'announced']))
        }
        third.join(WAIT_SECONDS * 1000L)
        boolean thirdWaited = third.alive
        processed.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        refresher.join(WAIT_SECONDS * 1000L)
        third.join(WAIT_SECONDS * 1000L)

        then: "the refresh has processed it, in its order"
        verifyAll {
            !thirdWaited
            !adder.alive
            !refresher.alive
            env.getProperty('announced.key', String).orElse(null) == 'announced'
            env.getProperty('order.value', String).orElse(null) == 'higher'
        }

        cleanup:
        processed.release.countDown()
        env.stop()
    }

    void "a property source added while a refresh starts that then fails is processed after it, as the thread adding it did: #how"() {
        given: "a property source that fails to be read once asked to, of a higher order than the one added"
        FailsWhenAsked flaky = new FailsWhenAsked()
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(PropertySource.of('higher', ['order.value': 'higher'], 100))
        env.addPropertySource(flaky)
        env.start()
        Throwable refreshFailure = null

        when:
        flaky.fail.set(true)
        Thread refresher = parkIn(env.stopping) {
            try {
                refresh.call(env)
            } catch (Throwable t) {
                refreshFailure = t
            }
        }
        Thread adder = daemon('adder') {
            env.addPropertySource(PropertySource.of('announced', ['announced.key': 'announced']))
        }
        adder.join(WAIT_SECONDS * 1000L)
        boolean adderWaited = adder.alive
        release(env.stopping, refresher)
        adder.join(WAIT_SECONDS * 1000L)

        then:
        verifyAll {
            !adderWaited
            !adder.alive
            refreshFailure instanceof IllegalArgumentException
            env.getProperty('announced.key', String).orElse(null) == 'announced'
            env.getProperty('order.value', String).orElse(null) == 'higher'
        }

        when: "the next refresh reads it"
        flaky.fail.set(false)
        env.refresh()

        then:
        env.getProperty('announced.key', String).get() == 'announced'

        cleanup:
        env.stopping.release.countDown()
        env.stop()

        where:
        how              | refresh
        'refresh'        | { DefaultEnvironment e -> e.refresh() }
        'refreshAndDiff' | { DefaultEnvironment e -> e.refreshAndDiff() }
    }

    void "a property source added once a refresh has read the property sources, while it compares what it has read, does not make the thread that adds it wait, and is processed"() {
        given: "a property source whose value, compared with the one it had, parks the thread that compares it, once armed"
        ComparedValue.PARKING.set(null)
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new ComparedValues())
        env.start()
        Parking comparing = new Parking()
        ComparedValue.PARKING.set(comparing)

        when:
        Thread refresher = parkIn(comparing) { env.refreshAndDiff() }
        Thread adder = daemon('adder') {
            env.addPropertySource(PropertySource.of('added', ['added.key': 'added']))
        }
        adder.join(WAIT_SECONDS * 1000L)
        boolean adderWaited = adder.alive
        release(comparing, refresher)
        adder.join(WAIT_SECONDS * 1000L)

        then:
        verifyAll {
            !adderWaited
            !adder.alive
            env.getProperty('added.key', String).orElse(null) == 'added'
        }

        cleanup:
        comparing.release.countDown()
        ComparedValue.PARKING.set(null)
        env.stop()
    }

    void "a property source that refreshes the environment while it is processed, as another thread adds one, does not deadlock"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.start()
        RefreshesWhenProcessed refreshing = new RefreshesWhenProcessed(env)

        when: "it refreshes once the other thread waits to process the property source it adds"
        Thread adder = daemon('adder') { env.addPropertySource(refreshing) }
        assert refreshing.processing.await(WAIT_SECONDS, TimeUnit.SECONDS)
        refreshing.other = daemon('other') { env.addPropertySource(PropertySource.of('other', ['other.key': 'other'])) }
        adder.join(WAIT_SECONDS * 1000L)
        refreshing.other.join(WAIT_SECONDS * 1000L)

        then:
        !adder.alive
        !refreshing.other.alive
        env.getProperty('refreshing.key', String).get() == 'refreshing'
        env.getProperty('other.key', String).get() == 'other'

        cleanup:
        if (!adder.alive && !refreshing.other.alive) {
            // stopping needs the lock that deadlocked threads would still hold
            env.stop()
        }
    }

    void "a property source that the refreshing thread adds once it has read the property sources is in what the refresh publishes and in its difference"() {
        given:
        AddsWhenStarted env = new AddsWhenStarted()
        env.addPropertySource(PropertySource.of('extra', ['extra.key': 'v1']))
        env.start()

        when:
        env.armed.set(true)
        Map<String, Object> diff = env.refreshAndDiff()

        then:
        env.seen == 'v2'
        diff.containsKey('extra.key')
        env.getProperty('extra.key', String).get() == 'v2'

        cleanup:
        env.stop()
    }

    void "a property source added after a refresh that fails as it starts to read is processed when it is added"() {
        given:
        FailsWhenNamed env = new FailsWhenNamed()
        env.start()

        when:
        env.fail.set(true)
        env.refresh()

        then:
        thrown(IllegalStateException)

        when:
        env.addPropertySource(PropertySource.of('later', ['later.key': 'later']))

        then:
        env.getProperty('later.key', String).orElse(null) == 'later'

        cleanup:
        env.stop()
    }

    void "a property source added by the name of one left to a refresh, while that one is processed after the refresh, is the one that stays"() {
        given:
        FailsWhenAsked flaky = new FailsWhenAsked()
        ResettingEnvironment env = new ResettingEnvironment()
        env.addPropertySource(flaky)
        env.start()

        when: "a property source is left to a refresh that fails, and is processed after it"
        flaky.fail.set(true)
        Thread refresher = parkIn(env.stopping) {
            try {
                env.refresh()
            } catch (IllegalArgumentException ignored) {
                // expected
            }
        }
        Thread first = daemon('first') {
            env.addPropertySource(PropertySource.of('replaced', ['replaced.key': 'first']))
        }
        first.join(WAIT_SECONDS * 1000L)
        assert !first.alive
        env.resetting.arm()
        env.stopping.release.countDown()
        assert env.resetting.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)

        and: "once the first has been added, another thread adds one by the same name while the first is processed"
        PropertySource second = PropertySource.of('replaced', ['replaced.key': 'second'])
        Thread adder = daemon('second') { env.addPropertySource(second) }
        awaitDoneOrBlocked(adder)
        release(env.resetting, refresher)
        adder.join(WAIT_SECONDS * 1000L)

        then: "the second is registered and its values are the ones looked up"
        !adder.alive
        env.propertySources.find { it.name == 'replaced' }.is(second)
        env.getProperty('replaced.key', String).get() == 'second'

        cleanup:
        env.stopping.release.countDown()
        env.resetting.release.countDown()
        env.stop()
    }

    void "a property source added by the code of one being processed, while a refresh waits for it, is in the properties right away"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.start()
        AddsWhenProcessed adding = new AddsWhenProcessed(env)

        when: "a refresh waits for the thread processing the property source, whose code then adds another one and looks it up"
        adding.arm()
        Thread adder = daemon('adder') { env.addPropertySource(adding) }
        assert adding.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread refresher = daemon('refresher') { env.refresh() }
        awaitWaiting(refresher)
        adding.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        refresher.join(WAIT_SECONDS * 1000L)

        then:
        !adder.alive
        !refresher.alive
        adding.seen == 'nested'
        env.getProperty('nested.value', String).get() == 'nested'

        cleanup:
        adding.release.countDown()
        env.stop()
    }

    void "a property source added by the name of one being processed, whose code refreshes the environment, is processed after it"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        env.start()
        RefreshesOnce first = new RefreshesOnce(env, 'first')

        when: "the first is processed, and its code refreshes the environment, which is parked, while another thread adds one by the same name"
        Thread adder = parkIn(env.stopping) { env.addPropertySource(first) }
        PropertySource second = PropertySource.of('watcher', ['watcher.value': 'second'])
        Thread other = daemon('other') { env.addPropertySource(second) }
        awaitDoneOrBlocked(other)
        release(env.stopping, adder)
        other.join(WAIT_SECONDS * 1000L)

        then: "the second is registered and its values are the ones looked up"
        !other.alive
        env.propertySources.find { it.name == 'watcher' }.is(second)
        env.getProperty('watcher.value', String).get() == 'second'

        cleanup:
        env.stopping.release.countDown()
        env.stop()
    }

    void "a property source added by code that resetCaches() runs and waits for, while another one is added, does not deadlock"() {
        given:
        AddsWhenResetting env = new AddsWhenResetting()
        env.start()

        when:
        env.armed.set(true)
        Thread adder = daemon('adder') { env.addPropertySource(PropertySource.of('outer', ['outer.key': 'outer'])) }
        adder.join(WAIT_SECONDS * 1000L)

        then:
        !adder.alive
        env.helperFinished
        env.getProperty('outer.key', String).orElse(null) == 'outer'
        env.getProperty('helper.key', String).orElse(null) == 'helper'

        cleanup:
        if (!adder.alive) {
            env.stop()
        }
    }

    void "a property source added by code that isRunning() runs, while another thread processes one, does not deadlock"() {
        given:
        AddsWhenAskedIfRunning env = new AddsWhenAskedIfRunning()
        env.start()
        ParksWhileProcessed processed = new ParksWhileProcessed(Collections.synchronizedList([]))

        when: "another thread processes a property source, and a thread whose isRunning() adds one adds another"
        processed.arm()
        Thread first = daemon('first') { env.addPropertySource(processed) }
        assert processed.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        env.armed.set(true)
        Thread second = daemon('second') { env.addPropertySource(PropertySource.of('second', ['second.key': 'second'])) }
        awaitDoneOrBlocked(second)
        processed.release.countDown()
        first.join(WAIT_SECONDS * 1000L)
        second.join(WAIT_SECONDS * 1000L)

        then:
        !first.alive
        !second.alive
        env.getProperty('nested.key', String).orElse(null) == 'nested'
        env.getProperty('second.key', String).orElse(null) == 'second'

        cleanup:
        processed.release.countDown()
        if (!first.alive && !second.alive) {
            // stopping needs the lock that deadlocked threads would still hold
            env.stop()
        }
    }

    void "a property source added while a refresh restarts the environment, by a thread that pauses once it finds it stopped, is read by the refresh"() {
        given:
        StopsThenParks env = new StopsThenParks()
        env.start()

        when: "the refresh is parked once it has stopped the environment, and the thread adding finds it stopped and pauses until the refresh is done"
        Thread refresher = parkIn(env.stopped) { env.refresh() }
        env.asking.arm()
        Thread adder = daemon('adder') { env.addPropertySource(PropertySource.of('added', ['added.key': 'added'])) }
        assert env.asking.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        release(env.stopped, refresher)
        release(env.asking, adder)

        then:
        env.getProperty('added.key', String).orElse(null) == 'added'

        cleanup:
        env.stopped.release.countDown()
        env.asking.release.countDown()
        env.stop()
    }

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
     * Waits until the thread waits, as a refresh does for what other threads are processing before
     * it starts. Polled, and bounded: no latch tells that a thread waits.
     */
    private static void awaitWaiting(Thread thread) {
        Set<Thread.State> waiting = EnumSet.of(Thread.State.WAITING, Thread.State.TIMED_WAITING, Thread.State.BLOCKED)
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (!waiting.contains(thread.state) && System.nanoTime() < deadline) {
            Thread.sleep(5)
        }
        assert waiting.contains(thread.state)
    }

    /**
     * Waits until the thread has finished, or waits for a lock. Polled, and bounded.
     */
    private static void awaitDoneOrBlocked(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (thread.alive && thread.state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.sleep(5)
        }
        assert !thread.alive || thread.state == Thread.State.BLOCKED
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
                if (!release.await(AddPropertySourceDuringRefreshSpec.PARK_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the parked thread was never released')
                }
                return true
            }
            return false
        }
    }

    /**
     * An environment without default property sources that, once armed, parks the thread that
     * stops it: a refresh, after it has taken the lock of the catalogs and before it reads the
     * property sources.
     */
    static class ParkingEnvironment extends DefaultEnvironment {
        final Parking stopping = new Parking()

        ParkingEnvironment() {
            super(AddPropertySourceDuringRefreshSpec.withoutDefaultPropertySources())
        }

        @Override
        Environment stop() {
            stopping.pass()
            return super.stop()
        }
    }

    /**
     * The same, that also parks, once armed, a thread that resets the caches of the running
     * environment, as one does right before it processes a property source added to it.
     */
    static class ResettingEnvironment extends ParkingEnvironment {
        final Parking resetting = new Parking()

        @Override
        protected void resetCaches() {
            if (isRunning()) {
                resetting.pass()
            }
            super.resetCaches()
        }
    }

    /**
     * An environment without default property sources that, once armed, replaces a property source
     * once it has started, and notes the value it reads right after.
     */
    static class AddsWhenStarted extends DefaultEnvironment {
        final AtomicBoolean armed = new AtomicBoolean()
        volatile String seen

        AddsWhenStarted() {
            super(AddPropertySourceDuringRefreshSpec.withoutDefaultPropertySources())
        }

        @Override
        Environment start() {
            super.start()
            if (armed.compareAndSet(true, false)) {
                addPropertySource(PropertySource.of('extra', ['extra.key': 'v2']))
                seen = getProperty('extra.key', String).orElse(null)
            }
            return this
        }
    }

    /**
     * An environment without default property sources whose active names, once armed, fail to be
     * read once, as the start reads them before it reads the property sources.
     */
    static class FailsWhenNamed extends DefaultEnvironment {
        final AtomicBoolean fail = new AtomicBoolean()

        FailsWhenNamed() {
            super(AddPropertySourceDuringRefreshSpec.withoutDefaultPropertySources())
        }

        @Override
        Set<String> getActiveNames() {
            if (fail.compareAndSet(true, false)) {
                throw new IllegalStateException('failed to read the active names')
            }
            return super.getActiveNames()
        }
    }

    /**
     * A property source of a lower order that, once armed, parks the thread that processes it, and
     * notes when it has been processed.
     */
    static class ParksWhileProcessed extends Parking implements PropertySource {
        private final List<String> events

        ParksWhileProcessed(List<String> events) {
            this.events = events
        }

        @Override
        String getName() { 'lower' }

        @Override
        int getOrder() { -100 }

        @Override
        Object get(String key) { key == 'order.value' || key == 'lower.key' ? 'lower' : null }

        @Override
        Iterator<String> iterator() {
            if (pass()) {
                events << 'processed'
            }
            return ['order.value', 'lower.key'].iterator()
        }
    }

    /**
     * An environment without default property sources that, once armed, parks a refresh once it
     * has stopped the environment, and the thread named {@code adder} once it has found it stopped.
     */
    static class StopsThenParks extends DefaultEnvironment {
        final Parking stopped = new Parking()
        final Parking asking = new Parking()

        StopsThenParks() {
            super(AddPropertySourceDuringRefreshSpec.withoutDefaultPropertySources())
        }

        @Override
        Environment stop() {
            Environment environment = super.stop()
            stopped?.pass()
            return environment
        }

        @Override
        boolean isRunning() {
            boolean running = super.isRunning()
            if (!running && Thread.currentThread().name == 'adder') {
                asking?.pass()
            }
            return running
        }
    }

    /**
     * An environment without default property sources whose resetCaches(), once armed, has another
     * thread add a property source and waits for it, bounded.
     */
    static class AddsWhenResetting extends DefaultEnvironment {
        final AtomicBoolean armed = new AtomicBoolean()
        volatile boolean helperFinished

        AddsWhenResetting() {
            super(AddPropertySourceDuringRefreshSpec.withoutDefaultPropertySources())
        }

        @Override
        protected void resetCaches() {
            if (armed?.compareAndSet(true, false)) {
                Thread helper = new Thread({ addPropertySource(PropertySource.of('helper', ['helper.key': 'helper'])) }, 'helper')
                helper.daemon = true
                helper.start()
                helper.join(AddPropertySourceDuringRefreshSpec.WAIT_SECONDS * 1000L)
                helperFinished = !helper.alive
            }
            super.resetCaches()
        }
    }

    /**
     * An environment without default property sources whose isRunning(), once armed, adds a
     * property source.
     */
    static class AddsWhenAskedIfRunning extends DefaultEnvironment {
        final AtomicBoolean armed = new AtomicBoolean()

        AddsWhenAskedIfRunning() {
            super(AddPropertySourceDuringRefreshSpec.withoutDefaultPropertySources())
        }

        @Override
        boolean isRunning() {
            if (armed?.compareAndSet(true, false)) {
                addPropertySource(PropertySource.of('nested', ['nested.key': 'nested']))
            }
            return super.isRunning()
        }
    }

    /**
     * A property source named {@code watcher} whose code, the first time it is processed, refreshes
     * the environment.
     */
    static class RefreshesOnce implements PropertySource {
        private final Environment env
        private final String value
        private final AtomicBoolean refreshed = new AtomicBoolean()

        RefreshesOnce(Environment env, String value) {
            this.env = env
            this.value = value
        }

        @Override
        String getName() { 'watcher' }

        @Override
        Object get(String key) { key == 'watcher.value' ? value : null }

        @Override
        Iterator<String> iterator() {
            if (refreshed.compareAndSet(false, true)) {
                env.refresh()
            }
            return ['watcher.value'].iterator()
        }
    }

    /**
     * A property source that, once armed, parks the thread that processes it, and then adds another
     * property source and notes the value it looks up right after.
     */
    static class AddsWhenProcessed extends Parking implements PropertySource {
        volatile String seen
        private final Environment env

        AddsWhenProcessed(Environment env) {
            this.env = env
        }

        @Override
        String getName() { 'adding' }

        @Override
        Object get(String key) { key == 'adding.key' ? 'adding' : null }

        @Override
        Iterator<String> iterator() {
            if (pass()) {
                env.addPropertySource(PropertySource.of('nested', ['nested.value': 'nested']))
                seen = env.getProperty('nested.value', String).orElse(null)
            }
            return ['adding.key'].iterator()
        }
    }

    /**
     * A property source without properties that, once armed, notes when it is read.
     */
    static class Noting implements PropertySource {
        final AtomicBoolean armed = new AtomicBoolean()
        private final List<String> events

        Noting(List<String> events) {
            this.events = events
        }

        @Override
        String getName() { 'noting' }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                events << 'read'
            }
            return Collections.emptyIterator()
        }
    }

    /**
     * A property source without properties that, once armed, needs the given lock to be read, and
     * notes when it does not get it in time.
     */
    static class NeedsLock implements PropertySource {
        final AtomicBoolean armed = new AtomicBoolean()
        volatile boolean timedOut
        private final ReentrantLock lock

        NeedsLock(ReentrantLock lock) {
            this.lock = lock
        }

        @Override
        String getName() { 'needs-lock' }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                // shorter than the waits for the threads, which a deadlock is reported by
                if (lock.tryLock(AddPropertySourceDuringRefreshSpec.WAIT_SECONDS.intdiv(2), TimeUnit.SECONDS)) {
                    lock.unlock()
                } else {
                    timedOut = true
                }
            }
            return Collections.emptyIterator()
        }
    }

    /**
     * A property source of a high order that fails to be read once asked to.
     */
    static class FailsWhenAsked implements PropertySource {
        final AtomicBoolean fail = new AtomicBoolean()

        @Override
        String getName() { 'flaky' }

        @Override
        int getOrder() { 200 }

        @Override
        Object get(String key) { key == 'flaky.key' ? 'flaky' : null }

        @Override
        Iterator<String> iterator() {
            if (fail.get()) {
                throw new IllegalArgumentException('failed to read the property source')
            }
            return ['flaky.key'].iterator()
        }
    }

    /**
     * A property source whose value is another one each time it is asked for, so that a refresh
     * compares the one it has read with the previous one.
     */
    static class ComparedValues implements PropertySource {
        @Override
        String getName() { 'compared' }

        @Override
        int getOrder() { Ordered.LOWEST_PRECEDENCE }

        @Override
        Object get(String key) { key == 'compared.value' ? new ComparedValue() : null }

        @Override
        Iterator<String> iterator() { ['compared.value'].iterator() }
    }

    /**
     * A value that parks the thread that compares it with another, once the parking is armed.
     */
    static class ComparedValue {
        static final AtomicReference<Parking> PARKING = new AtomicReference<>()

        @Override
        boolean equals(Object other) {
            PARKING.get()?.pass()
            return other instanceof ComparedValue
        }

        @Override
        int hashCode() { 1 }
    }

    /**
     * A property source that, the first time it is iterated, waits for another thread to wait for
     * the lock of the catalogs, which the thread processing it holds, and then refreshes the
     * environment.
     */
    static class RefreshesWhenProcessed implements PropertySource {
        final CountDownLatch processing = new CountDownLatch(1)
        volatile Thread other
        private final Environment env
        private final AtomicBoolean iterated = new AtomicBoolean()

        RefreshesWhenProcessed(Environment env) {
            this.env = env
        }

        @Override
        String getName() { 'refreshing' }

        @Override
        Object get(String key) { key == 'refreshing.key' ? 'refreshing' : null }

        @Override
        Iterator<String> iterator() {
            if (iterated.compareAndSet(false, true)) {
                processing.countDown()
                // polled, and bounded: no latch tells that a thread waits for a monitor
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AddPropertySourceDuringRefreshSpec.WAIT_SECONDS)
                while ((other == null || other.state != Thread.State.BLOCKED) && System.nanoTime() < deadline) {
                    Thread.sleep(5)
                }
                env.refresh()
            }
            return ['refreshing.key'].iterator()
        }
    }
}

package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.core.order.Ordered
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

/**
 * A property source added to a running environment, outside a read of its property sources, as a
 * refresh starts. The thread that adds it does not wait for the refresh, which holds the lock of the
 * catalogs from before it reads until it publishes, and the property source is processed once, in
 * its order. Every wait is bounded.
 */
@Timeout(60)
class AddPropertySourceWhileRefreshingSpec extends Specification {

    private static final int WAIT_SECONDS = 10
    /**
     * How long a parked thread waits to be released: longer than a test waits for another thread,
     * so that a thread that waits for a parked one is seen to wait, rather than being let go by the
     * park giving up.
     */
    private static final int PARK_SECONDS = WAIT_SECONDS * 2

    void "a property source added just before a #how starts does not make the thread that adds it wait for it, and is processed once, in its order"() {
        given: "a locator that, once armed, parks the refresh, and a property source of a lower order that parks the thread adding it once it is registered"
        Locator locator = new Locator()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(PropertySource.of('higher', ['order.value': 'higher'], 100))
        env.start()
        ParksBeforeProcessing lower = new ParksBeforeProcessing()
        Map<String, Object> diff = null

        when: "the property source is registered, and a refresh starts and is parked in the locator before it is processed"
        lower.arm()
        Thread adder = daemon('adder') { env.addPropertySource(lower) }
        assert lower.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread refresher = parkIn(locator) {
            if (how == 'refresh') {
                env.refresh()
            } else {
                diff = env.refreshAndDiff()
            }
        }
        lower.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)

        then: "the thread that adds it does not wait for the refresh"
        !adder.alive

        when:
        release(locator, refresher)

        then: "the refresh has processed it once, in its order, so the property source of the higher order keeps the key"
        env.getProperty('lower.key', String).get() == 'lower'
        env.getProperty('order.value', String).get() == 'higher'
        how == 'refresh' || diff.containsKey('lower.key')

        cleanup:
        lower.release.countDown()
        locator.release.countDown()
        env.stop()

        where:
        how << ['refresh', 'refreshAndDiff']
    }

    void "a thread that holds a lock a registered property source needs, and adds a property source just before a refresh starts, does not deadlock with the refresh"() {
        given: "a registered property source that needs a lock when it is read, and a locator that, once armed, parks the refresh"
        ReentrantLock lock = new ReentrantLock()
        NeedsLock locked = new NeedsLock(lock)
        Locator locator = new Locator()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(locked)
        env.start()
        ParksBeforeProcessing lower = new ParksBeforeProcessing()

        when: "a thread takes the lock, adds a property source, and a refresh starts and is parked in the locator before the thread processes it"
        lower.arm()
        Thread adder = daemon('adder') {
            lock.lock()
            try {
                env.addPropertySource(lower)
            } finally {
                lock.unlock()
            }
        }
        assert lower.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread refresher = parkIn(locator) { env.refresh() }
        lower.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)

        then: "the thread does not wait for the refresh, and lets go of the lock"
        !adder.alive

        when:
        release(locator, refresher)

        then: "the refresh reads the property source that needs the lock, and the one added"
        !locked.timedOut.get()
        env.getProperty('locked.key', String).get() == 'locked'
        env.getProperty('lower.key', String).get() == 'lower'

        cleanup:
        lower.release.countDown()
        locator.release.countDown()
        env.stop()
    }

    void "a refresh that starts while a property source added outside a read is processed reads the property sources once it has been processed"() {
        given:
        List<String> events = Collections.synchronizedList([])
        PropertySourcesLocator locator = new PropertySourcesLocator() {
            @Override
            Collection<PropertySource> load(Environment environment) {
                events << 'located'
                return []
            }
        }
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(PropertySource.of('higher', ['order.value': 'higher'], 100))
        env.start()
        events.clear()
        ParksWhileProcessed lower = new ParksWhileProcessed(events)

        when: "a refresh starts while the property source is being processed"
        lower.arm()
        Thread adder = daemon('adder') { env.addPropertySource(lower) }
        assert lower.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread refresher = daemon('refresher') { env.refresh() }
        lower.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        refresher.join(WAIT_SECONDS * 1000L)

        then: "both finish, and the refresh has read the property sources after the property source was processed"
        !adder.alive
        !refresher.alive
        events == ['processed', 'located']
        env.getProperty('order.value', String).get() == 'higher'
        env.getProperty('lower.key', String).get() == 'lower'

        cleanup:
        lower.release.countDown()
        env.stop()
    }

    void "a property source added while a #how waits to start is left to it, without waiting for it, and is processed in its order"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('higher', ['order.value': 'higher'], 100))
        env.start()
        ParksWhileProcessed processed = new ParksWhileProcessed(Collections.synchronizedList([]))
        Map<String, Object> diff = null

        when: "a refresh waits for a property source another thread is processing, and a third thread adds one meanwhile"
        processed.arm()
        Thread adder = daemon('adder') { env.addPropertySource(processed) }
        assert processed.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread refresher = daemon('refresher') {
            if (how == 'refresh') {
                env.refresh()
            } else {
                diff = env.refreshAndDiff()
            }
        }
        awaitWaiting(refresher)
        changeWhileParked {
            env.addPropertySource(PropertySource.of('announced', ['order.value': 'announced', 'announced.key': 'announced']))
        }
        boolean visibleBeforeRefresh = env.getProperty('announced.key', String).present
        processed.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        refresher.join(WAIT_SECONDS * 1000L)

        then: "the refresh has processed it, in its order"
        !adder.alive
        !refresher.alive
        !visibleBeforeRefresh
        env.getProperty('announced.key', String).get() == 'announced'
        env.getProperty('order.value', String).get() == 'higher'
        how == 'refresh' || diff.containsKey('announced.key')

        cleanup:
        processed.release.countDown()
        env.stop()

        where:
        how << ['refresh', 'refreshAndDiff']
    }

    void "a property source added while a refresh waits to start, which then fails, is processed after it, as the thread adding it did"() {
        given: "a property source that fails to be read once asked to, which the refresh reads before the one added"
        FailsWhenAsked flaky = new FailsWhenAsked()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('higher', ['order.value': 'higher'], 100))
        env.addPropertySource(flaky)
        env.start()
        ParksWhileProcessed processed = new ParksWhileProcessed(Collections.synchronizedList([]))
        Throwable refreshFailure = null

        when: "a refresh that fails waits for a property source another thread is processing, and a third thread adds one meanwhile"
        processed.arm()
        Thread adder = daemon('adder') { env.addPropertySource(processed) }
        assert processed.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        flaky.fail.set(true)
        Thread refresher = daemon('refresher') {
            try {
                env.refresh()
            } catch (Throwable t) {
                refreshFailure = t
            }
        }
        awaitWaiting(refresher)
        changeWhileParked {
            env.addPropertySource(PropertySource.of('announced', ['announced.key': 'announced']))
        }
        processed.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        refresher.join(WAIT_SECONDS * 1000L)

        then:
        !adder.alive
        !refresher.alive
        refreshFailure instanceof IllegalArgumentException
        env.getProperty('announced.key', String).get() == 'announced'
        env.getProperty('lower.key', String).get() == 'lower'

        cleanup:
        processed.release.countDown()
        env.stop()
    }

    void "a property source whose values come from the environment, added while a refresh waits to start, which then fails, gives those of what stays published"() {
        given: "a property source of a higher order than the one added, that fails to be read once asked to"
        CountingLocator locator = new CountingLocator()
        FailsWhenAsked flaky = new FailsWhenAsked(200)
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(flaky)
        env.start()
        ParksWhileProcessed processed = new ParksWhileProcessed(Collections.synchronizedList([]))

        when: "a refresh that fails waits for a property source another thread is processing, and a third thread adds one meanwhile"
        processed.arm()
        Thread adder = daemon('adder') { env.addPropertySource(processed) }
        assert processed.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        flaky.fail.set(true)
        Thread refresher = daemon('refresher') {
            try {
                env.refresh()
            } catch (IllegalArgumentException ignored) {
                // expected
            }
        }
        awaitWaiting(refresher)
        changeWhileParked {
            env.addPropertySource(new CopiesProperty(env, 'loc.value', 'derived.url', 100))
        }
        processed.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        refresher.join(WAIT_SECONDS * 1000L)

        then:
        !adder.alive
        !refresher.alive
        env.getProperty('loc.value', String).get() == 'v1'
        env.getProperty('derived.url', String).get() == 'v1'

        cleanup:
        processed.release.countDown()
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
        env.stop()
    }

    void "a thread that keeps replacing a property source does not keep refreshes from finishing"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('hot', ['hot.value': 0]))
        env.start()
        AtomicBoolean stop = new AtomicBoolean()
        AtomicInteger last = new AtomicInteger()

        when: "one thread replaces a property source in a loop while another refreshes"
        Thread adder = daemon('adder') {
            int i = 0
            while (!stop.get()) {
                i++
                env.addPropertySource(PropertySource.of('hot', ['hot.value': i]))
                last.set(i)
            }
        }
        int refreshes = 0
        while (refreshes < 20) {
            env.refresh()
            refreshes++
        }
        stop.set(true)
        adder.join(WAIT_SECONDS * 1000L)

        then: "every refresh finishes, and the next refresh reads the last property source added"
        !adder.alive
        env.refresh().getProperty('hot.value', Integer).get() == last.get()

        cleanup:
        stop.set(true)
        env.stop()
    }

    void "a property source that adds another while it is processed, as a refresh waits to start, reads the one it added"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.start()
        AddsWhenProcessed source = new AddsWhenProcessed(env)
        Throwable failure = null

        when: "a refresh waits for the thread processing it, which then adds another and looks it up"
        Thread adder = daemon('adder') {
            try {
                env.addPropertySource(source)
            } catch (Throwable t) {
                failure = t
            }
        }
        assert source.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread refresher = daemon('refresher') {
            try {
                env.refresh()
            } catch (Throwable t) {
                failure = t
            }
        }
        awaitWaiting(refresher)
        source.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        refresher.join(WAIT_SECONDS * 1000L)

        then: "it is processed at once, as the thread adding it holds the lock of the catalogs"
        !adder.alive
        !refresher.alive
        failure == null
        source.observed == 'nested'

        cleanup:
        source.release.countDown()
        env.stop()
    }

    void "a property source left to a refresh that fails does not overwrite a newer one by its name, added and processed after it"() {
        given:
        FailsWhenArmed locator = new FailsWhenArmed()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.start()
        ParksAfterFailedRefresh first = new ParksAfterFailedRefresh()
        PropertySource second = PropertySource.of('watcher', ['watcher.value': 'second'])
        Throwable refreshFailure = null

        when: "the first is registered and its thread parked before processing it, and a refresh starts and is parked in the locator"
        Thread adder = daemon('adder') { env.addPropertySource(first) }
        assert first.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        locator.armed.set(true)
        Thread refresher = daemon('refresher') {
            try {
                env.refresh()
            } catch (Throwable t) {
                refreshFailure = t
            } finally {
                // 5.2.x does not process it on the refresher's thread: this lets the test go on there
                first.replayEntered.countDown()
            }
        }
        assert locator.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)

        and: "the first's thread goes on, and the refresh fails"
        first.release.countDown()
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (adder.alive && adder.state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.sleep(5)
        }
        locator.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        assert first.replayEntered.await(WAIT_SECONDS, TimeUnit.SECONDS)

        and: "a newer one is added and processed while the refresh is about to process the first on top"
        env.addPropertySource(second)
        String afterSecond = env.getProperty('watcher.value', String).orElse(null)
        first.replayRelease.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then: "the newer one keeps its values"
        !adder.alive
        !refresher.alive
        refreshFailure instanceof IllegalStateException
        afterSecond == 'second'
        env.propertySources.find { it.name == 'watcher' }.is(second)
        env.getProperty('watcher.value', String).get() == 'second'

        cleanup:
        first.release.countDown()
        first.replayRelease.countDown()
        locator.release.countDown()
        env.stop()
    }

    void "a property source added by the name of one whose thread is about to process it, as a refresh waits to start, is processed after that refresh fails"() {
        given: "a locator that fails once armed"
        FailsWhenArmed locator = new FailsWhenArmed()
        locator.release.countDown()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.start()
        ParksWhenAskedForConvention earlier = new ParksWhenAskedForConvention('watcher')
        ParksWhileProcessed processed = new ParksWhileProcessed(Collections.synchronizedList([]))
        PropertySource newer = PropertySource.of('watcher', ['watcher.value': 'newer'])
        Throwable refreshFailure = null

        when: "the earlier one is registered and its thread parked before it processes it"
        earlier.arm()
        Thread earlierAdder = daemon('earlier-adder') { env.addPropertySource(earlier) }
        assert earlier.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)

        and: "a refresh that fails waits for another thread processing a property source"
        processed.arm()
        Thread adder = daemon('adder') { env.addPropertySource(processed) }
        assert processed.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        locator.armed.set(true)
        Thread refresher = daemon('refresher') {
            try {
                env.refresh()
            } catch (Throwable t) {
                refreshFailure = t
            }
        }
        awaitWaiting(refresher)

        and: "the newer one is added and left to the refresh, and then the thread of the earlier one goes on"
        changeWhileParked {
            env.addPropertySource(newer)
        }
        earlier.release.countDown()
        earlierAdder.join(WAIT_SECONDS * 1000L)
        processed.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        refresher.join(WAIT_SECONDS * 1000L)

        then: "the newer one is processed once the refresh has failed, as the thread adding it would have"
        !earlierAdder.alive
        !adder.alive
        !refresher.alive
        refreshFailure instanceof IllegalStateException
        env.propertySources.find { it.name == 'watcher' }.is(newer)
        env.getProperty('watcher.value', String).get() == 'newer'

        cleanup:
        earlier.release.countDown()
        processed.release.countDown()
        env.stop()
    }

    void "a property source added while a refresh reads the property sources is registered, and read by the next refresh, as before"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)
        env.start()

        when: "it is added while the refresh is parked in the property source it processes last"
        Thread refresher = parkIn(gate) { env.refresh() }
        changeWhileParked {
            env.addPropertySource(PropertySource.of('during', ['during.key': 'during']))
        }
        release(gate, refresher)

        then: "the thread that adds it does not wait, and it is registered"
        env.propertySources*.name.contains('during')
        env.getProperty('app.value', String).get() == 'a'

        and: "the next refresh reads it"
        env.refresh().getProperty('during.key', String).get() == 'during'

        cleanup:
        gate.release.countDown()
        env.stop()
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
     * The same, with the given property sources locator.
     */
    private static ApplicationContextConfiguration withLocator(PropertySourcesLocator locator) {
        new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() { ['test'] }

            @Override
            boolean isEnableDefaultPropertySources() { false }

            @Override
            Collection<PropertySourcesLocator> getPropertySourcesLocators() { [locator] }
        }
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

    /**
     * Waits until the thread waits, as a refresh does for what other threads are processing before
     * it starts. Polled, and bounded: no latch tells that a thread waits.
     */
    private static void awaitWaiting(Thread thread) {
        Set<Thread.State> waiting = EnumSet.of(Thread.State.WAITING, Thread.State.TIMED_WAITING, Thread.State.BLOCKED)
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        Thread.State state = thread.state
        while (!waiting.contains(state) && System.nanoTime() < deadline) {
            Thread.sleep(5)
            state = thread.state
        }
        assert waiting.contains(state)
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
                if (!release.await(AddPropertySourceWhileRefreshingSpec.PARK_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the parked thread was never released')
                }
                return true
            }
            return false
        }
    }

    /**
     * A property sources locator that parks the read, once armed.
     */
    static class Locator extends Parking implements PropertySourcesLocator {
        @Override
        Collection<PropertySource> load(Environment environment) {
            pass()
            return []
        }
    }

    /**
     * A property source without properties, processed after the others, that parks the thread that
     * iterates it, once armed.
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
     * A property source of a lower order that, once armed, parks the thread that asks for its
     * convention: the thread adding it does, once it is registered and before it is processed.
     */
    static class ParksBeforeProcessing extends Parking implements PropertySource {
        @Override
        String getName() { 'lower' }

        @Override
        int getOrder() { -100 }

        @Override
        Object get(String key) { key == 'order.value' || key == 'lower.key' ? 'lower' : null }

        @Override
        Iterator<String> iterator() { ['order.value', 'lower.key'].iterator() }

        @Override
        PropertySource.PropertyConvention getConvention() {
            pass()
            return PropertySource.PropertyConvention.JAVA_PROPERTIES
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
     * A property source that takes the given lock when it is read, waiting for it a bounded time.
     */
    static class NeedsLock implements PropertySource {
        final AtomicBoolean timedOut = new AtomicBoolean()
        private final ReentrantLock lock

        NeedsLock(ReentrantLock lock) {
            this.lock = lock
        }

        @Override
        String getName() { 'locked' }

        @Override
        Object get(String key) { key == 'locked.key' ? 'locked' : null }

        @Override
        Iterator<String> iterator() {
            if (!lock.tryLock(AddPropertySourceWhileRefreshingSpec.PARK_SECONDS, TimeUnit.SECONDS)) {
                timedOut.set(true)
                throw new IllegalStateException('the lock the property source needs was not released')
            }
            try {
                return ['locked.key'].iterator()
            } finally {
                lock.unlock()
            }
        }
    }

    /**
     * A property source that, the first time it is iterated, waits to be released, then adds another
     * property source and looks up its property.
     */
    static class AddsWhenProcessed implements PropertySource {
        final CountDownLatch entered = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        volatile String observed
        private final Environment env
        private final AtomicBoolean once = new AtomicBoolean(true)

        AddsWhenProcessed(Environment env) {
            this.env = env
        }

        @Override
        String getName() { 'nested-parent' }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (once.compareAndSet(true, false)) {
                entered.countDown()
                release.await(AddPropertySourceWhileRefreshingSpec.PARK_SECONDS, TimeUnit.SECONDS)
                env.addPropertySource(PropertySource.of('nested', ['nested.value': 'nested']))
                observed = env.getProperty('nested.value', String).orElse('missing')
            }
            return Collections.emptyIterator()
        }
    }

    /**
     * A property source that parks the thread adding it where it asks for its convention, and, once
     * a refresh it was left to has failed, the refresher where it asks for it again.
     */
    static class ParksAfterFailedRefresh implements PropertySource {
        final CountDownLatch entered = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        final CountDownLatch replayEntered = new CountDownLatch(1)
        final CountDownLatch replayRelease = new CountDownLatch(1)
        private final AtomicBoolean once = new AtomicBoolean(true)

        @Override
        String getName() { 'watcher' }

        @Override
        Object get(String key) { 'first' }

        @Override
        Iterator<String> iterator() { ['watcher.value'].iterator() }

        @Override
        PropertySource.PropertyConvention getConvention() {
            if (once.compareAndSet(true, false)) {
                entered.countDown()
                release.await(AddPropertySourceWhileRefreshingSpec.PARK_SECONDS, TimeUnit.SECONDS)
            }
            if (Thread.currentThread().name == 'refresher') {
                replayEntered.countDown()
                replayRelease.await(AddPropertySourceWhileRefreshingSpec.PARK_SECONDS, TimeUnit.SECONDS)
            }
            return PropertySource.PropertyConvention.JAVA_PROPERTIES
        }
    }

    /**
     * A property source without properties that, once armed, parks the thread that asks for its
     * convention: the thread adding it does, once it is registered and before it is processed.
     */
    static class ParksWhenAskedForConvention extends Parking implements PropertySource {
        private final String name

        ParksWhenAskedForConvention(String name) {
            this.name = name
        }

        @Override
        String getName() { name }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() { Collections.emptyIterator() }

        @Override
        PropertySource.PropertyConvention getConvention() {
            pass()
            return PropertySource.PropertyConvention.JAVA_PROPERTIES
        }
    }

    /**
     * A property sources locator that, once armed, parks the read and then fails.
     */
    static class FailsWhenArmed implements PropertySourcesLocator {
        final AtomicBoolean armed = new AtomicBoolean()
        final CountDownLatch entered = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)

        @Override
        Collection<PropertySource> load(Environment environment) {
            if (armed.compareAndSet(true, false)) {
                entered.countDown()
                release.await(AddPropertySourceWhileRefreshingSpec.PARK_SECONDS, TimeUnit.SECONDS)
                throw new IllegalStateException('the locator failed')
            }
            return []
        }
    }

    /**
     * A property sources locator that returns a new property source each time it loads them.
     */
    static class CountingLocator implements PropertySourcesLocator {
        final AtomicInteger loads = new AtomicInteger()

        @Override
        Collection<PropertySource> load(Environment environment) {
            return [PropertySource.of('loc', ['loc.value': 'v' + loads.incrementAndGet()], 50)]
        }
    }

    /**
     * A property source that fails to be read once {@link #fail} is set.
     */
    static class FailsWhenAsked implements PropertySource {
        final AtomicBoolean fail = new AtomicBoolean()
        private final int order

        FailsWhenAsked(int order = 0) {
            this.order = order
        }

        @Override
        String getName() { 'flaky' }

        @Override
        int getOrder() { order }

        @Override
        Object get(String key) { key == 'flaky.key' ? 'flaky' : null }

        @Override
        Iterator<String> iterator() {
            if (fail.get()) {
                throw new IllegalArgumentException('failed to iterate the property source')
            }
            return ['flaky.key'].iterator()
        }
    }

    /**
     * A property source whose one property is what the environment has for another, when it is asked for.
     */
    static class CopiesProperty implements PropertySource {
        private final Environment env
        private final String from
        private final String to
        private final int order

        CopiesProperty(Environment env, String from, String to, int order) {
            this.env = env
            this.from = from
            this.to = to
            this.order = order
        }

        @Override
        String getName() { 'copier' }

        @Override
        int getOrder() { order }

        @Override
        Object get(String key) { key == to ? env.getProperty(from, String).orElse(null) : null }

        @Override
        Iterator<String> iterator() { [to].iterator() }
    }

    /**
     * A property source that, the first time it is iterated, waits until another thread waits for the
     * lock of the catalogs, which the thread that processes it holds, and then refreshes the
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
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AddPropertySourceWhileRefreshingSpec.WAIT_SECONDS)
                while ((other == null || other.state != Thread.State.BLOCKED) && System.nanoTime() < deadline) {
                    Thread.sleep(5)
                }
                env.refresh()
            }
            return ['refreshing.key'].iterator()
        }
    }
}

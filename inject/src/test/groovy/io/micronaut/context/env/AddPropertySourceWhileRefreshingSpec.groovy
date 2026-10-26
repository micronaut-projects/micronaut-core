package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

/**
 * Property sources added while a refresh starts, runs or fails, as the scenarios of #14137 have
 * them. No add waits for a refresh, and no refresh waits for an add: an add publishes when it has
 * processed its property source, and a refresh reads what is registered when it takes the
 * registered property sources. The threads are parked at fixed points, and every wait is bounded.
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

    void "a property source added just before a #how starts does not make the thread that adds it wait, is published at once, and is processed by the #how in its order"() {
        given: "a locator that, once armed, parks the refresh, and a property source of a lower order that, once armed, parks the thread adding it before it processes it"
        Locator locator = new Locator()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(PropertySource.of('higher', ['order.value': 'higher'], 100))
        env.start()
        ParksBeforeProcessing lower = new ParksBeforeProcessing()
        Map<String, Object> diff = null

        when: "the thread adding it is parked, a refresh starts and is parked in the locator, before it takes the registered property sources, and the thread goes on"
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
        List<String> namesDuringRefresh = env.propertySources*.name
        String orderDuringRefresh = env.getProperty('order.value', String).orElse(null)
        String keyDuringRefresh = env.getProperty('lower.key', String).orElse(null)

        then: "the thread that adds it does not wait for the refresh, and the property source is registered and published at once, on top"
        !adder.alive
        namesDuringRefresh == ['higher', 'lower']
        keyDuringRefresh == 'lower'
        orderDuringRefresh == 'lower'

        when:
        release(locator, refresher)

        then: "the refresh, which takes the registered property sources after it, has processed it in its order, and reports only that"
        env.getProperty('lower.key', String).get() == 'lower'
        env.getProperty('order.value', String).get() == 'higher'
        env.propertySources*.name == ['higher', 'lower']
        how == 'refresh' || diff == ['order.value': 'lower']

        cleanup:
        lower.release.countDown()
        locator.release.countDown()
        env.stop()

        where:
        how << ['refresh', 'refreshAndDiff']
    }

    void "a thread that holds a lock a registered property source needs, and adds a property source just before a refresh starts, does not wait for the refresh, which then reads both"() {
        given: "a registered property source that needs a lock when it is read, and a locator that, once armed, parks the refresh"
        ReentrantLock lock = new ReentrantLock()
        NeedsLock locked = new NeedsLock(lock)
        Locator locator = new Locator()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(locked)
        env.start()
        ParksBeforeProcessing lower = new ParksBeforeProcessing()

        when: "a thread takes the lock and adds a property source, and a refresh starts and is parked in the locator before the thread processes it"
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
        String addedDuringRefresh = env.getProperty('lower.key', String).orElse(null)

        then: "the thread does not wait for the refresh, lets go of the lock, and what it added is visible at once"
        !adder.alive
        !lock.isLocked()
        addedDuringRefresh == 'lower'

        when:
        release(locator, refresher)

        then: "the refresh reads the property source that needs the lock, and the one added"
        !locked.timedOut.get()
        env.getProperty('locked.key', String).get() == 'locked'
        env.getProperty('lower.key', String).get() == 'lower'
        env.propertySources*.name == ['locked', 'lower']

        cleanup:
        lower.release.countDown()
        locator.release.countDown()
        env.stop()
    }

    void "a refresh that runs while a property source added outside a read is processed does not wait for it, an add made meanwhile is visible at once, and the parked add lands on top of both until the next refresh reads them in their order: the refresh #outcome"() {
        given: "a property source of a higher order, one that fails to be read once asked to, and a locator that notes when it runs"
        List<String> events = Collections.synchronizedList([])
        PropertySourcesLocator locator = new PropertySourcesLocator() {
            @Override
            Collection<PropertySource> load(Environment environment) {
                events << 'located'
                return []
            }
        }
        FailsWhenAsked flaky = new FailsWhenAsked()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(PropertySource.of('higher', ['order.value': 'higher'], 100))
        env.addPropertySource(flaky)
        env.start()
        events.clear()
        ParksWhileProcessed lower = new ParksWhileProcessed(events)
        Throwable refreshFailure = null

        when: "a refresh runs while another thread is parked processing a property source it adds"
        lower.arm()
        Thread adder = daemon('adder') { env.addPropertySource(lower) }
        assert lower.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        flaky.fail.set(outcome == 'fails')
        Thread refresher = daemon('refresher') {
            try {
                env.refresh()
            } catch (Throwable t) {
                refreshFailure = t
            }
        }
        refresher.join(WAIT_SECONDS * 1000L)
        boolean refreshedWhileProcessed = !refresher.alive
        List<String> eventsWhileProcessed = new ArrayList<>(events)
        boolean registeredWhileProcessed = env.propertySources*.name.contains('lower')

        and: "a third thread adds one meanwhile, and then the parked add goes on"
        changeWhileParked {
            env.addPropertySource(PropertySource.of('announced', ['order.value': 'announced', 'announced.key': 'announced']))
        }
        String orderWhileProcessed = env.getProperty('order.value', String).orElse(null)
        boolean lowerWhileProcessed = env.containsProperty('lower.key')
        lower.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)

        then: "the refresh did not wait for the add, which registers its property source only once it has processed it"
        refreshedWhileProcessed
        eventsWhileProcessed == ['located']
        !registeredWhileProcessed
        outcome == 'succeeds' ? refreshFailure == null : refreshFailure instanceof IllegalArgumentException

        and: "the add made meanwhile was visible at once, on top"
        orderWhileProcessed == 'announced'
        !lowerWhileProcessed

        and: "the parked add is published on top of both, without being processed again, and registered after the other"
        !adder.alive
        events == ['located', 'processed']
        env.getProperty('order.value', String).get() == 'lower'
        env.getProperty('lower.key', String).get() == 'lower'
        env.getProperty('announced.key', String).get() == 'announced'
        env.getProperty('flaky.key', String).get() == 'flaky'
        env.propertySources*.name == ['higher', 'flaky', 'announced', 'lower']

        when: "the environment is refreshed again"
        flaky.fail.set(false)
        env.refresh()

        then: "every property source is read in its order"
        events == ['located', 'processed', 'located']
        env.getProperty('order.value', String).get() == 'higher'
        env.getProperty('lower.key', String).get() == 'lower'
        env.getProperty('announced.key', String).get() == 'announced'

        cleanup:
        lower.release.countDown()
        env.stop()

        where:
        outcome << ['succeeds', 'fails']
    }

    void "a property source added while a #how is parked before it takes the registered property sources is left to it, the #how does not wait for another thread's add, and both are processed in their order"() {
        given: "a locator that, once armed, parks the refresh"
        Locator locator = new Locator()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(PropertySource.of('higher', ['order.value': 'higher'], 100))
        env.start()
        ParksWhileProcessed processed = new ParksWhileProcessed(Collections.synchronizedList([]))
        Map<String, Object> diff = null

        when: "a refresh starts while another thread processes a property source it adds, and is parked in the locator, before it takes the registered property sources"
        processed.arm()
        Thread adder = daemon('adder') { env.addPropertySource(processed) }
        assert processed.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread refresher = parkIn(locator) {
            if (how == 'refresh') {
                env.refresh()
            } else {
                diff = env.refreshAndDiff()
            }
        }

        and: "a third thread adds one meanwhile, and then the other thread goes on"
        changeWhileParked {
            env.addPropertySource(PropertySource.of('announced', ['order.value': 'announced', 'announced.key': 'announced']))
        }
        boolean visibleBeforeRefresh = env.getProperty('announced.key', String).present
        processed.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        String lowerBeforeRefresh = env.getProperty('lower.key', String).orElse(null)
        String orderBeforeRefresh = env.getProperty('order.value', String).orElse(null)
        release(locator, refresher)

        then: "no thread that adds waits for the refresh: the one added while it had not taken the registered property sources is left to it, and the other is published at once, on top"
        !adder.alive
        !visibleBeforeRefresh
        lowerBeforeRefresh == 'lower'
        orderBeforeRefresh == 'lower'

        and: "the refresh has processed both in their order, and reports what it has changed since the other thread published"
        env.getProperty('announced.key', String).get() == 'announced'
        env.getProperty('lower.key', String).get() == 'lower'
        env.getProperty('order.value', String).get() == 'higher'
        env.propertySources*.name == ['higher', 'announced', 'lower']
        how == 'refresh' || diff.keySet() == ['announced.key', 'order.value'] as Set
        how == 'refresh' || diff['order.value'] == 'lower'

        cleanup:
        processed.release.countDown()
        locator.release.countDown()
        env.stop()

        where:
        how << ['refresh', 'refreshAndDiff']
    }

    void "a property source added before a refresh that then fails takes the registered property sources stays registered and unpublished until the next refresh, while one another thread published meanwhile stays"() {
        given: "a property source that fails to be read once asked to, and a locator that, once armed, parks the refresh"
        Locator locator = new Locator()
        FailsWhenAsked flaky = new FailsWhenAsked()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(PropertySource.of('higher', ['order.value': 'higher'], 100))
        env.addPropertySource(flaky)
        env.start()
        ParksWhileProcessed processed = new ParksWhileProcessed(Collections.synchronizedList([]))
        PropertySource announced = PropertySource.of('announced', ['announced.key': 'announced'])
        Throwable refreshFailure = null

        when: "a refresh that fails starts while another thread processes a property source it adds, and is parked in the locator"
        processed.arm()
        Thread adder = daemon('adder') { env.addPropertySource(processed) }
        assert processed.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        flaky.fail.set(true)
        Thread refresher = parkIn(locator) {
            try {
                env.refresh()
            } catch (Throwable t) {
                refreshFailure = t
            }
        }

        and: "a third thread adds one meanwhile, and then the other thread goes on"
        changeWhileParked {
            env.addPropertySource(announced)
        }
        processed.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        release(locator, refresher)

        then: "no thread that adds waits for the refresh, and the refresh fails"
        !adder.alive
        refreshFailure instanceof IllegalArgumentException

        and: "the one left to it stays registered but unpublished, as the refresh published nothing, and the one the other thread published stays"
        env.propertySources.find { it.name == 'announced' }.is(announced)
        !env.containsProperty('announced.key')
        env.getProperty('lower.key', String).get() == 'lower'
        env.getProperty('order.value', String).get() == 'lower'
        env.getProperty('flaky.key', String).get() == 'flaky'

        when: "the next refresh succeeds"
        flaky.fail.set(false)
        env.refresh()

        then: "it reads every property source in its order"
        env.getProperty('announced.key', String).get() == 'announced'
        env.getProperty('lower.key', String).get() == 'lower'
        env.getProperty('order.value', String).get() == 'higher'

        cleanup:
        processed.release.countDown()
        locator.release.countDown()
        env.stop()
    }

    void "a property source whose values come from the environment, added before a refresh that then fails takes the registered property sources, shows nothing that refresh read, and the next refresh gives it what that one reads"() {
        given: "a locator that locates a new value each time and, once armed, parks the read before it does, and a property source of a higher order than the one added that fails to be read once asked to"
        ParkingCountingLocator locator = new ParkingCountingLocator()
        FailsWhenAsked flaky = new FailsWhenAsked(200)
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(flaky)
        env.start()
        ParksWhileProcessed processed = new ParksWhileProcessed(Collections.synchronizedList([]))
        CopiesProperty copier = new CopiesProperty(env, 'loc.value', 'derived.url', 100)
        Throwable refreshFailure = null

        when: "a refresh that fails starts while another thread processes a property source it adds, and is parked in the locator"
        processed.arm()
        Thread adder = daemon('adder') { env.addPropertySource(processed) }
        assert processed.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        flaky.fail.set(true)
        Thread refresher = parkIn(locator) {
            try {
                env.refresh()
            } catch (Throwable t) {
                refreshFailure = t
            }
        }

        and: "a third thread adds a property source that copies a property of the environment, and then the other thread goes on"
        changeWhileParked {
            env.addPropertySource(copier)
        }
        boolean copiedBeforeRefresh = env.containsProperty('derived.url')
        processed.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        release(locator, refresher)

        then: "no thread that adds waits for the refresh, which located a new value and failed"
        !adder.alive
        refreshFailure instanceof IllegalArgumentException
        locator.loads.get() == 2

        and: "the copier stays registered, and neither the value the refresh located nor the copy it took of it is published"
        !copiedBeforeRefresh
        env.propertySources.find { it.name == 'copier' }.is(copier)
        env.getProperty('loc.value', String).get() == 'v1'
        !env.getProperty('derived.url', String).present
        env.getProperty('lower.key', String).get() == 'lower'

        when: "the next refresh succeeds"
        flaky.fail.set(false)
        env.refresh()

        then: "the copy is taken from what it reads"
        env.getProperty('loc.value', String).get() == 'v3'
        env.getProperty('derived.url', String).get() == 'v3'
        env.getProperty('lower.key', String).get() == 'lower'

        cleanup:
        processed.release.countDown()
        locator.release.countDown()
        env.stop()
    }

    void "a property source that refreshes the environment while it is processed, as another thread adds one, does not deadlock, and the other thread does not wait for it"() {
        given:
        Map<String, Object> data = ['app.value': 'a']
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        env.start()
        RefreshesWhenProcessed refreshing = new RefreshesWhenProcessed(env)

        when: "another thread adds a property source while it is processed, and it then refreshes the environment"
        Thread adder = daemon('adder') { env.addPropertySource(refreshing) }
        assert refreshing.processing.await(WAIT_SECONDS, TimeUnit.SECONDS)
        changeWhileParked {
            env.addPropertySource(PropertySource.of('other', ['other.key': 'other']))
        }
        String otherWhileProcessed = env.getProperty('other.key', String).orElse(null)
        boolean refreshingWhileProcessed = env.containsProperty('refreshing.key')
        data.put('app.value', 'b')
        refreshing.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)

        then: "the other thread did not wait, and what it added was visible at once"
        otherWhileProcessed == 'other'
        !refreshingWhileProcessed

        and: "the refresh ran on the thread that processes the property source, which is then published on top of what the refresh read"
        !adder.alive
        refreshing.refreshed
        env.getProperty('app.value', String).get() == 'b'
        env.getProperty('other.key', String).get() == 'other'
        env.getProperty('refreshing.key', String).get() == 'refreshing'
        env.propertySources*.name == ['data', 'other', 'refreshing']

        when: "the environment is refreshed again"
        env.refresh()

        then: "it reads all three"
        env.getProperty('refreshing.key', String).get() == 'refreshing'
        env.getProperty('other.key', String).get() == 'other'

        cleanup:
        refreshing.release.countDown()
        env.stop()
    }

    void "a thread that keeps replacing a property source does not keep refreshes from finishing, and the last property source it has added gives the value"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('hot', ['hot.value': 0]))
        env.start()
        AtomicBoolean stop = new AtomicBoolean()
        AtomicInteger last = new AtomicInteger()
        AtomicInteger refreshes = new AtomicInteger()
        CountDownLatch adding = new CountDownLatch(1)

        when: "one thread replaces a property source in a loop while another refreshes"
        Thread adder = daemon('adder') {
            int i = 0
            while (!stop.get()) {
                i++
                env.addPropertySource(PropertySource.of('hot', ['hot.value': i]))
                last.set(i)
                adding.countDown()
            }
        }
        assert adding.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread refresher = daemon('refresher') {
            20.times {
                env.refresh()
                refreshes.incrementAndGet()
            }
        }
        refresher.join(WAIT_SECONDS * 1000L)
        boolean refreshedWhileAdding = !refresher.alive
        boolean addingThroughout = adder.alive
        stop.set(true)
        adder.join(WAIT_SECONDS * 1000L)

        then: "every refresh finishes while the other thread keeps adding"
        refreshedWhileAdding
        addingThroughout
        refreshes.get() == 20
        !adder.alive

        and: "the last property source added is registered and gives the value, before the next refresh and after it"
        env.propertySources.find { it.name == 'hot' }.get('hot.value') == last.get()
        env.getProperty('hot.value', Integer).get() == last.get()
        env.refresh().getProperty('hot.value', Integer).get() == last.get()

        cleanup:
        stop.set(true)
        env.stop()
    }

    void "a property source that adds another while it is processed, as a refresh runs, reads the one it added, and the refresh does not wait for it"() {
        given:
        Map<String, Object> data = ['app.value': 'a']
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        env.start()
        AddsWhenProcessed source = new AddsWhenProcessed(env)
        Throwable failure = null

        when: "a refresh runs while the thread adding it is parked in it, before it adds another and looks it up"
        Thread adder = daemon('adder') {
            try {
                env.addPropertySource(source)
            } catch (Throwable t) {
                failure = t
            }
        }
        assert source.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        data.put('app.value', 'b')
        Thread refresher = daemon('refresher') {
            try {
                env.refresh()
            } catch (Throwable t) {
                failure = t
            }
        }
        refresher.join(WAIT_SECONDS * 1000L)
        boolean refreshedWhileParked = !refresher.alive
        String refreshedValue = env.getProperty('app.value', String).orElse(null)
        boolean registeredWhileParked = env.propertySources*.name.contains('nested-parent')
        source.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)

        then: "the refresh did not wait for the add, and published what it read, without the property source being added"
        refreshedWhileParked
        refreshedValue == 'b'
        !registeredWhileParked

        and: "the thread adding it saw the one it added at once"
        !adder.alive
        failure == null
        source.observed == 'nested'

        and: "both are published on top of what the refresh read, and registered"
        env.getProperty('nested.value', String).get() == 'nested'
        env.getProperty('app.value', String).get() == 'b'
        env.propertySources*.name.containsAll(['data', 'nested-parent', 'nested'])

        cleanup:
        source.release.countDown()
        env.stop()
    }

    void "a property source whose add is in progress when a refresh that fails starts is published without waiting for the refresh, and a newer one by its name, added after it, replaces it"() {
        given:
        FailsWhenArmed locator = new FailsWhenArmed()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.start()
        ParksAfterFailedRefresh first = new ParksAfterFailedRefresh()
        PropertySource second = PropertySource.of('watcher', ['watcher.value': 'second'])
        Throwable refreshFailure = null

        when: "the first's thread is parked before processing it, and a refresh starts and is parked in the locator"
        Thread adder = daemon('adder') { env.addPropertySource(first) }
        assert first.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        locator.armed.set(true)
        Thread refresher = daemon('refresher') {
            try {
                env.refresh()
            } catch (Throwable t) {
                refreshFailure = t
            }
        }
        assert locator.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)

        and: "the first's thread goes on while the refresh is parked"
        first.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        boolean addedWhileParked = !adder.alive
        String duringRefresh = env.getProperty('watcher.value', String).orElse(null)

        and: "the refresh fails"
        locator.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)
        String afterFailure = env.getProperty('watcher.value', String).orElse(null)
        PropertySource registeredAfterFailure = env.propertySources.find { it.name == 'watcher' }

        and: "a newer one is added and processed after it"
        env.addPropertySource(second)
        String afterSecond = env.getProperty('watcher.value', String).orElse(null)

        then: "the first did not wait for the refresh, and was published at once"
        addedWhileParked
        duringRefresh == 'first'

        and: "the failed refresh did not process it, and left it published and registered"
        !refresher.alive
        refreshFailure instanceof IllegalStateException
        first.replayEntered.count == 1
        afterFailure == 'first'
        registeredAfterFailure.is(first)

        and: "the newer one replaces it"
        afterSecond == 'second'
        env.propertySources.find { it.name == 'watcher' }.is(second)
        env.getProperty('watcher.value', String).get() == 'second'

        when: "the environment is refreshed again"
        env.refresh()

        then: "it reads the newer one"
        env.propertySources.find { it.name == 'watcher' }.is(second)
        env.getProperty('watcher.value', String).get() == 'second'

        cleanup:
        first.release.countDown()
        first.replayRelease.countDown()
        locator.release.countDown()
        env.stop()
    }

    void "a property source whose add is in progress when a refresh that fails starts stays on top until a later refresh reads it in its order"() {
        given: "a property source of a higher order than the one added"
        FailsWhenArmed locator = new FailsWhenArmed()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(PropertySource.of('higher', ['watcher.value': 'higher'], 100))
        env.start()
        ParksAfterFailedRefresh lower = new ParksAfterFailedRefresh()
        Throwable refreshFailure = null

        when: "its thread is parked before processing it, and a refresh starts and is parked in the locator"
        Thread adder = daemon('adder') { env.addPropertySource(lower) }
        assert lower.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        locator.armed.set(true)
        Thread refresher = daemon('refresher') {
            try {
                env.refresh()
            } catch (Throwable t) {
                refreshFailure = t
            }
        }
        assert locator.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)

        and: "its thread goes on while the refresh is parked, and the refresh then fails"
        lower.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        boolean addedWhileParked = !adder.alive
        String duringRefresh = env.getProperty('watcher.value', String).orElse(null)
        locator.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)
        String afterFailure = env.getProperty('watcher.value', String).orElse(null)

        and: "another refresh reads it in its order"
        env.refresh()

        then: "the add did not wait for the refresh, and stayed on top of the properties when the refresh failed"
        addedWhileParked
        duringRefresh == 'first'
        !refresher.alive
        refreshFailure instanceof IllegalStateException
        lower.replayEntered.count == 1
        afterFailure == 'first'

        and: "the later refresh has read it in its order, below the property source of the higher order"
        env.propertySources*.name.containsAll(['higher', 'watcher'])
        env.getProperty('watcher.value', String).get() == 'higher'

        cleanup:
        lower.release.countDown()
        lower.replayRelease.countDown()
        locator.release.countDown()
        env.stop()
    }

    void "a property source whose add is in progress when a refresh that fails starts, read by a later refresh, is not processed on top by a third refresh that fails while another add is in progress"() {
        given: "a property source of a higher order than the one added"
        ScriptedLocator locator = new ScriptedLocator()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(PropertySource.of('higher', ['watcher.value': 'higher'], 100))
        env.start()
        ParksAfterFailedRefresh lower = new ParksAfterFailedRefresh()
        ParksWhileProcessed processed = new ParksWhileProcessed(Collections.synchronizedList([]))
        Throwable[] failures = new Throwable[2]

        when: "its thread goes on while a refresh is parked in the locator, which then fails"
        Thread adder = daemon('adder') { env.addPropertySource(lower) }
        assert lower.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        locator.park.set(true)
        Thread refresher = daemon('refresher') {
            try {
                env.refresh()
            } catch (Throwable t) {
                failures[0] = t
            }
        }
        assert locator.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        lower.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        boolean addedWhileParked = !adder.alive
        String duringRefresh = env.getProperty('watcher.value', String).orElse(null)
        locator.fail.set(true)
        locator.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        and: "another refresh reads it in its order"
        locator.fail.set(false)
        env.refresh()
        String afterRefresh = env.getProperty('watcher.value', String).orElse(null)

        and: "a third refresh, which fails, runs while a thread is parked processing a property source it adds"
        processed.arm()
        Thread processor = daemon('processor') { env.addPropertySource(processed) }
        assert processed.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        locator.fail.set(true)
        Thread third = daemon('third') {
            try {
                env.refresh()
            } catch (Throwable t) {
                failures[1] = t
            }
        }
        third.join(WAIT_SECONDS * 1000L)
        boolean thirdWhileParked = !third.alive
        String afterThird = env.getProperty('watcher.value', String).orElse(null)
        processed.release.countDown()
        processor.join(WAIT_SECONDS * 1000L)

        then: "the add did not wait for the first refresh, and stayed on top until the later refresh read it in its order"
        addedWhileParked
        duringRefresh == 'first'
        !refresher.alive
        failures[0] instanceof IllegalStateException
        afterRefresh == 'higher'

        and: "the third refresh failed without waiting for the add in progress, and published nothing"
        thirdWhileParked
        failures[1] instanceof IllegalStateException
        afterThird == 'higher'

        and: "no refresh has processed it on top, and the add that was in progress is published on top once it ends"
        !processor.alive
        lower.replayEntered.count == 1
        env.getProperty('watcher.value', String).get() == 'higher'
        env.getProperty('lower.key', String).get() == 'lower'

        cleanup:
        lower.release.countDown()
        lower.replayRelease.countDown()
        locator.release.countDown()
        processed.release.countDown()
        env.stop()
    }

    void "a property source added by the name of one whose add is still in progress, after a refresh that fails at once, is published and registered at once, and the add called first publishes nothing"() {
        given: "a locator that fails once armed"
        FailsWhenArmed locator = new FailsWhenArmed()
        locator.release.countDown()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.start()
        ParksWhenAskedForConvention earlier = new ParksWhenAskedForConvention('watcher')
        ParksWhileProcessed processed = new ParksWhileProcessed(Collections.synchronizedList([]))
        PropertySource newer = PropertySource.of('watcher', ['watcher.value': 'newer'])
        Throwable refreshFailure = null

        when: "the earlier one's thread is parked before it processes it"
        earlier.arm()
        Thread earlierAdder = daemon('earlier-adder') { env.addPropertySource(earlier) }
        assert earlier.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)

        and: "a refresh that fails runs while another thread is parked processing a property source"
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
        refresher.join(WAIT_SECONDS * 1000L)
        boolean refreshedWhileParked = !refresher.alive

        and: "the newer one is added, and then the thread of the earlier one goes on"
        changeWhileParked {
            env.addPropertySource(newer)
        }
        String afterNewer = env.getProperty('watcher.value', String).orElse(null)
        PropertySource registeredAfterNewer = env.propertySources.find { it.name == 'watcher' }
        earlier.release.countDown()
        earlierAdder.join(WAIT_SECONDS * 1000L)
        PropertySource registeredAfterEarlier = env.propertySources.find { it.name == 'watcher' }
        processed.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)

        then: "the refresh failed without waiting for the threads adding property sources"
        refreshedWhileParked
        refreshFailure instanceof IllegalStateException

        and: "the newer one was published and registered at once"
        afterNewer == 'newer'
        registeredAfterNewer.is(newer)

        and: "the add called later wins: the earlier one, whose add ends last, neither replaces it in the registry nor in the properties"
        !earlierAdder.alive
        !adder.alive
        registeredAfterEarlier.is(newer)
        env.getProperty('watcher.value', String).get() == 'newer'
        env.getProperty('lower.key', String).get() == 'lower'

        when: "the environment is refreshed again"
        env.refresh()

        then: "it reads the property sources that are registered"
        env.propertySources.find { it.name == 'watcher' }.is(newer)
        env.getProperty('watcher.value', String).get() == 'newer'
        env.getProperty('lower.key', String).get() == 'lower'

        cleanup:
        earlier.release.countDown()
        processed.release.countDown()
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
     * A property source of a lower order that, once armed, parks the thread that asks for its
     * convention: the thread adding it does, before it processes and registers it.
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
            // qualified: unqualified, Groovy resolves the name through get(String) of this property source
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
     * A property sources locator that, once armed, parks the read, and then returns a new property
     * source each time it loads them.
     */
    static class ParkingCountingLocator extends Parking implements PropertySourcesLocator {
        final AtomicInteger loads = new AtomicInteger()

        @Override
        Collection<PropertySource> load(Environment environment) {
            pass()
            return [PropertySource.of('loc', ['loc.value': 'v' + loads.incrementAndGet()], 50)]
        }
    }

    /**
     * A property source that, the first time it is iterated, waits to be released, and then
     * refreshes the environment.
     */
    static class RefreshesWhenProcessed implements PropertySource {
        final CountDownLatch processing = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        volatile boolean refreshed
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
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                if (!release.await(AddPropertySourceWhileRefreshingSpec.PARK_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the property source was never released')
                }
                env.refresh()
                refreshed = true
            }
            return ['refreshing.key'].iterator()
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
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                release.await(AddPropertySourceWhileRefreshingSpec.PARK_SECONDS, TimeUnit.SECONDS)
                env.addPropertySource(PropertySource.of('nested', ['nested.value': 'nested']))
                observed = env.getProperty('nested.value', String).orElse('missing')
            }
            return Collections.emptyIterator()
        }
    }

    /**
     * A property source that parks the thread adding it where it asks for its convention, and,
     * should a thread named 'refresher' process it, that thread where it asks for it again.
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
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
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
     * convention: the thread adding it does, before it processes it.
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
     * A property sources locator that parks the read once {@link #park} is set, and fails it while
     * {@link #fail} is set.
     */
    static class ScriptedLocator implements PropertySourcesLocator {
        final AtomicBoolean park = new AtomicBoolean()
        final AtomicBoolean fail = new AtomicBoolean()
        final CountDownLatch reached = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)

        @Override
        Collection<PropertySource> load(Environment environment) {
            if (park.compareAndSet(true, false)) {
                reached.countDown()
                release.await(AddPropertySourceWhileRefreshingSpec.PARK_SECONDS, TimeUnit.SECONDS)
            }
            if (fail.get()) {
                throw new IllegalStateException('the locator failed')
            }
            return []
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
}

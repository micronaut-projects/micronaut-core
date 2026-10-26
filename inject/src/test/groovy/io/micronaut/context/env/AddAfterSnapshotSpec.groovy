package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.core.order.Ordered
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Property sources added while a start or a refresh reads the property sources, once the refresh
 * has taken the snapshot of the added property sources. Such an add is processed on its own thread,
 * published at once and kept as a late add of the refresh, the latest by each name, and the refresh
 * publishes the late adds with what it has read, in their order. The threads are parked at fixed
 * points, and every wait is bounded.
 */
@Timeout(60)
class AddAfterSnapshotSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "property sources added while a #refresh processes the property sources, one while the add of the other is in progress, are each visible once added, the refresh does not wait for the add in progress, and the locators run once"() {
        given: "a locator that counts its loads, and a property source that, once armed, parks the thread that processes it"
        Gate gate = new Gate()
        ParkingPropertySource first = new ParkingPropertySource('first', ['first.key': 'first', 'shared.key': 'first'])
        AtomicInteger loads = new AtomicInteger()
        PropertySourcesLocator locator = new PropertySourcesLocator() {
            @Override
            Collection<PropertySource> load(Environment environment) {
                loads.incrementAndGet()
                return [PropertySource.of('located', ['located.key': 'located'], 5)]
            }
        }
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)
        if (refresh == 'refresh') {
            env.start()
        }
        loads.set(0)

        when: "one is added while the refresh is parked in the gate and parks the thread adding it while it is processed, and another is added meanwhile"
        Thread refresher = parkIn(gate) { refresh == 'refresh' ? env.refresh() : env.start() }
        first.arm()
        Thread firstAdder = daemon('first-adder') { env.addPropertySource(first) }
        assert first.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        changeWhileParked {
            env.addPropertySource(PropertySource.of('second', ['second.key': 'second', 'shared.key': 'second']))
        }

        then: "the second add waited for neither the refresh nor the first add, and is registered and visible at once"
        env.getProperty('second.key', String).orElse(null) == 'second'
        env.getProperty('shared.key', String).orElse(null) == 'second'
        env.propertySources*.name.contains('second')

        and: "the first is neither registered nor visible before its add publishes it"
        !env.containsProperty('first.key')
        !env.propertySources*.name.contains('first')

        when: "the refresh is released while the first add is still parked"
        release(gate, refresher)

        then: "the refresh has not waited for the first add, and publishes the second with what it has read"
        firstAdder.alive
        env.getProperty('second.key', String).get() == 'second'
        env.getProperty('shared.key', String).get() == 'second'
        env.getProperty('located.key', String).get() == 'located'
        env.getProperty('app.value', String).get() == 'a'
        !env.containsProperty('first.key')

        when: "the first add is released"
        release(first, firstAdder)

        then: "it is published on top of what the refresh and the second add have published"
        env.getProperty('first.key', String).get() == 'first'
        env.getProperty('shared.key', String).get() == 'first'
        env.getProperty('second.key', String).get() == 'second'
        env.getProperty('located.key', String).get() == 'located'
        env.getProperty('app.value', String).get() == 'a'
        env.propertySources*.name.containsAll(['first', 'second'])

        and: "the locators were run once, by the refresh"
        loads.get() == 1

        and: "a later refresh reads both in the order they were registered, which keeps the value the adds published"
        env.refresh().getProperty('shared.key', String).get() == 'first'
        env.getProperty('first.key', String).get() == 'first'
        env.getProperty('second.key', String).get() == 'second'
        env.getProperty('located.key', String).get() == 'located'

        cleanup:
        gate.release.countDown()
        first.release.countDown()
        env.stop()

        where:
        refresh << ['start', 'refresh']
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/13914')
    void "a property source whose add began during a refresh and ends after it, and one added once the refresh has ended, wait for nothing, and the add that ends last is on top until the next refresh reads both in their order"() {
        given: "a property source with an immutable list that, once armed, parks the thread adding it while it is processed, and one of a higher order that sets an element of that list"
        Gate gate = new Gate()
        ParkingPropertySource late = new ParkingPropertySource('late', ['foo': List.of('a'), 'late.key': 'late'])
        PropertySource x = PropertySource.of('x', ['x.ok': 'x', 'foo[0]': 'y'], 10)
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)
        env.start()

        when: "the first is added while a refresh is parked in the gate, and its add is parked while it is processed"
        Thread refresher = parkIn(gate) { env.refresh() }
        late.arm()
        Thread lateAdder = daemon('late-adder') { env.addPropertySource(late) }
        assert late.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)

        and: "the refresh is released and ends while that add is parked"
        release(gate, refresher)
        boolean addParkedAfterRefresh = lateAdder.alive
        boolean lateVisibleAfterRefresh = env.containsProperty('late.key')
        boolean lateRegisteredAfterRefresh = env.propertySources*.name.contains('late')

        and: "the second is added meanwhile"
        changeWhileParked {
            env.addPropertySource(x)
        }
        String xAfterAdd = env.getProperty('x.ok', String).orElse(null)
        String fooAfterAdd = env.getProperty('foo[0]', String).orElse(null)

        and: "the parked add is released"
        release(late, lateAdder)

        then: "the refresh did not wait for the parked add, which was neither visible nor registered before it ended"
        addParkedAfterRefresh
        !lateVisibleAfterRefresh
        !lateRegisteredAfterRefresh

        and: "the second add did not wait for it either, and was visible at once"
        xAfterAdd == 'x'
        fooAfterAdd == 'y'

        and: "the add that ended last is on top of what the refresh and the other add published, and both are registered in the order their adds ended"
        env.getProperty('late.key', String).get() == 'late'
        env.getProperty('foo[0]', String).get() == 'a'
        env.getProperty('x.ok', String).get() == 'x'
        env.getProperty('app.value', String).get() == 'a'
        env.propertySources*.name.findAll { it in ['x', 'late'] } == ['x', 'late']

        when: "the environment is refreshed again"
        env.refresh()

        then: "it reads both in their order, and the element set into a copy of the immutable list does not fail it"
        env.getProperty('foo[0]', String).get() == 'y'
        env.getProperty('foo', List).get() == ['y']
        env.getProperty('late.key', String).get() == 'late'
        env.getProperty('x.ok', String).get() == 'x'
        env.getProperty('app.value', String).get() == 'a'

        cleanup:
        gate.release.countDown()
        late.release.countDown()
        env.stop()
    }

    void "a property source removed and added again while a refresh processes the property sources is visible once added, and the refresh keeps it without reporting it as its change"() {
        given:
        Gate gate = new Gate()
        PropertySource first = PropertySource.of('first', ['app.value': 'old'])
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(first)
        env.addPropertySource(gate)
        env.start()
        PropertySource replacement = PropertySource.of('first', ['app.value': 'new'])
        Map<String, Object> diff = null

        when: "another thread removes it while the refresh is parked"
        Thread refresher = parkIn(gate) { diff = env.refreshAndDiff() }
        changeWhileParked {
            env.removePropertySource(first)
        }

        then: "it is unregistered, and its properties stay"
        !env.propertySources*.name.contains('first')
        env.getProperty('app.value', String).get() == 'old'

        when: "another thread adds one of the same name while the refresh is still parked"
        changeWhileParked {
            env.addPropertySource(replacement)
        }

        then: "its properties are visible at once"
        env.getProperty('app.value', String).get() == 'new'

        when:
        release(gate, refresher)

        then: "the refresh keeps it after what it has read, and does not report it as a change of its own"
        env.getProperty('app.value', String).get() == 'new'
        env.propertySources.find { it.name == 'first' }.is(replacement)
        diff == [:]

        and: "the next refresh reads it"
        env.refresh().getProperty('app.value', String).get() == 'new'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source added while a refresh processes the property sources, that waits for another thread to add a property source each time it is read, lets that add finish at once, when it is added and when the next refresh reads it"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)
        env.start()
        WaitsForAddEachTime waiting = new WaitsForAddEachTime(env)

        when: "it is added while the refresh is parked, which reads it on the adding thread"
        Thread refresher = parkIn(gate) { env.refresh() }
        changeWhileParked {
            env.addPropertySource(waiting)
        }
        String duringRefresh = env.getProperty('waits.key', String).orElse(null)
        String otherDuringRefresh = env.getProperty('other.key', String).orElse(null)
        release(gate, refresher)

        then: "the addition it waits for finishes at once, both are visible at once, and the refresh does not read it again"
        waiting.addedInTime == [true]
        duringRefresh == 'waits'
        otherDuringRefresh == 'other'

        and: "the refresh keeps both with what it has read, and each is registered once it is processed"
        env.getProperty('waits.key', String).get() == 'waits'
        env.getProperty('other.key', String).get() == 'other'
        env.getProperty('app.value', String).get() == 'a'
        env.propertySources*.name == ['data', 'gate', 'other1', 'waits']

        when: "the next refresh reads it on the refreshing thread"
        env.refresh()

        then: "the addition it waits for finishes at once there too"
        waiting.addedInTime == [true, true]
        env.getProperty('waits.key', String).get() == 'waits'
        env.getProperty('other.key', String).get() == 'other'
        env.propertySources*.name == ['data', 'gate', 'other1', 'waits', 'other2']

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source added again as the same instance with other values while a #refresh is in progress is processed again and its new values are visible at once; the refresh publishes what it has read of it if it reads it itself: #added"() {
        given:
        Gate gate = new Gate()
        Map<String, Object> values = ['m.value': 'v1']
        PropertySource mutable = new MapPropertySource('mutable', values)
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        if (added == 'registered before the refresh') {
            env.addPropertySource(mutable)
        }
        env.addPropertySource(gate)
        if (refresh == 'refresh') {
            env.start()
        }

        when: "it is added again with other values while the refresh is parked"
        Thread refresher = parkIn(gate) { refresh == 'refresh' ? env.refresh() : env.start() }
        if (added == 'added during the refresh') {
            changeWhileParked {
                env.addPropertySource(mutable)
            }
        }
        String beforeAddedAgain = env.getProperty('m.value', String).orElse(null)
        changeWhileParked {
            values.put('m.value', 'v2')
            env.addPropertySource(mutable)
        }
        String whileParked = env.getProperty('m.value', String).orElse(null)
        release(gate, refresher)

        then: "adding the registered instance again processed it again, on top, at once"
        beforeAddedAgain == 'v1'
        whileParked == 'v2'

        and: "a refresh that has taken it in its snapshot publishes what it has read of it, as 5.2.x did; one that has not keeps the new values on top, and it stays registered"
        env.getProperty('m.value', String).get() == afterRefresh
        env.propertySources.find { it.name == 'mutable' }.is(mutable)

        when:
        Map<String, Object> diff = env.refreshAndDiff()

        then: "the next refresh reads the new values"
        env.getProperty('m.value', String).get() == 'v2'
        diff == nextDiff

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        refresh   | added                           | afterRefresh | nextDiff
        'start'   | 'registered before the refresh' | 'v1'         | ['m.value': 'v1']
        'refresh' | 'registered before the refresh' | 'v1'         | ['m.value': 'v1']
        'start'   | 'added during the refresh'      | 'v2'         | [:]
        'refresh' | 'added during the refresh'      | 'v2'         | [:]
    }

    void "a property source that the code of another one adds, while the other one is added during a refresh, is published as registered, also once a refresh reads the other one and it adds another"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(gate)
        env.start()
        AddsEachTime adding = new AddsEachTime(env)
        Throwable failure = null

        when: "it is added while the refresh is parked: its add processes it, and it adds the nested one"
        Thread refresher = parkIn(gate) {
            try {
                env.refresh()
            } catch (Throwable t) {
                failure = t
            }
        }
        changeWhileParked {
            env.addPropertySource(adding)
        }
        String nestedDuringRefresh = env.getProperty('nested.key', String).orElse(null)
        String addingDuringRefresh = env.getProperty('adding.key', String).orElse(null)
        release(gate, refresher)

        then: "both were visible at once"
        nestedDuringRefresh == 'n1'
        addingDuringRefresh == 'adding'

        and: "the refresh keeps both, and the nested one published is the one registered"
        failure == null
        env.getProperty('adding.key', String).get() == 'adding'
        env.getProperty('nested.key', String).get() == 'n1'
        env.propertySources.find { it.name == 'nested' }.get('nested.key') == 'n1'

        when: "the environment is refreshed again, which reads it, so that it adds another nested one on the refreshing thread"
        env.refresh()

        then: "the nested one added last is published and registered"
        env.getProperty('adding.key', String).get() == 'adding'
        env.getProperty('nested.key', String).get() == 'n2'
        env.propertySources.find { it.name == 'nested' }.get('nested.key') == 'n2'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source added during a refresh, whose values come from the environment, gives those of the properties published when it is added, until the next refresh reads it in its order"() {
        given:
        Gate gate = new Gate()
        CountingLocator locator = new CountingLocator()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(gate)
        env.start()

        when: "it is added while the refresh is parked, after the refresh has located the property source again"
        Thread refresher = parkIn(gate) { env.refresh() }
        changeWhileParked {
            env.addPropertySource(new CopiesProperty(env, 'loc.value', 'copy.value', 100))
        }
        String locDuringRefresh = env.getProperty('loc.value', String).orElse(null)
        String copyDuringRefresh = env.getProperty('copy.value', String).orElse(null)
        release(gate, refresher)

        then: "it was processed at once, on top of the published properties"
        locDuringRefresh == 'v1'
        copyDuringRefresh == 'v1'

        and: "the refresh publishes it with the values it was processed with, without running its code again"
        env.getProperty('loc.value', String).get() == 'v2'
        env.getProperty('copy.value', String).get() == 'v1'
        env.propertySources*.name == ['gate', 'copier']

        when:
        env.refresh()

        then: "the next refresh reads it in its order, after the located property source"
        env.getProperty('loc.value', String).get() == 'v3'
        env.getProperty('copy.value', String).get() == 'v3'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a registered property source whose values come from the environment, added again during a refresh, is processed again with what is published then, and the refresh publishes what it computes itself, as 5.2.x did"() {
        given:
        Gate gate = new Gate()
        CountingLocator locator = new CountingLocator()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        CopiesProperty derived = new CopiesProperty(env, 'loc.value', 'derived.url', 100)
        env.addPropertySource(derived)
        env.addPropertySource(gate)
        env.start()
        Map<String, Object> diff = null

        expect:
        env.getProperty('derived.url', String).get() == 'v1'

        when: "it is added again, as a configuration watch adds every registered property source again, once the refresh has read it"
        Thread refresher = parkIn(gate) { diff = env.refreshAndDiff() }
        changeWhileParked {
            env.addPropertySource(derived)
        }
        String duringRefresh = env.getProperty('derived.url', String).orElse(null)
        release(gate, refresher)

        then: "the add processed it again on top of the published properties, which the refresh had not changed yet"
        duringRefresh == 'v1'

        and: "the refresh, which has read it itself, publishes what it has computed, and reports its own changes"
        env.getProperty('loc.value', String).get() == 'v2'
        env.getProperty('derived.url', String).get() == 'v2'
        diff == ['loc.value': 'v1', 'derived.url': 'v1']
        env.propertySources.find { it.name == 'copier' }.is(derived)

        when:
        env.refresh()

        then: "the next refresh computes it from what it reads"
        env.getProperty('loc.value', String).get() == 'v3'
        env.getProperty('derived.url', String).get() == 'v3'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source added during a refresh, by the name of one a locator returns, is published after the located one of the same order, whose other properties stay"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(new Locator(PropertySource.of('remote', ['a': '1', 'b': '1'], 10))))
        env.addPropertySource(gate)
        env.start()
        PropertySource added = PropertySource.of('remote', ['a': '2'], 10)

        when:
        Thread refresher = parkIn(gate) { env.refresh() }
        changeWhileParked {
            env.addPropertySource(added)
        }
        String aDuringRefresh = env.getProperty('a', String).orElse(null)
        String bDuringRefresh = env.getProperty('b', String).orElse(null)
        release(gate, refresher)

        then: "it was processed at once, on top of the published properties"
        aDuringRefresh == '2'
        bDuringRefresh == '1'

        and: "the refresh publishes it after the located one of the same order, and it is registered"
        env.getProperty('a', String).get() == '2'
        env.getProperty('b', String).get() == '1'
        env.propertySources*.name == ['gate', 'remote']
        env.propertySources.find { it.name == 'remote' }.is(added)

        when:
        env.refresh()

        then: "the next refresh reads both in their order: the registered one, then the located one of the same order"
        env.getProperty('a', String).get() == '1'
        env.getProperty('b', String).get() == '1'
        env.propertySources.find { it.name == 'remote' }.is(added)

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/13982')
    void "a property source of a lower order that sets a list a registered one sets an element of, added #relativeToSnapshot the #refresh takes the registered property sources, is processed by the refresh in its order: #kind"() {
        given: "a registered property source that sets an element of a list, a locator that parks the refresh before it takes the registered property sources, and a gate that parks it after"
        Locator locator = new Locator()
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(PropertySource.of('p', ['p.ok': 'p', 'foo[0]': 'x'], 100))
        env.addPropertySource(gate)
        if (refresh == 'refresh') {
            env.start()
        }
        Map<String, Object> values = ['foo': List.of('a'), 'a.ok': 'a']
        if (kind == 'fails part way') {
            values['a.bad[99999999999]'] = 'x'
        }
        Parking parking = relativeToSnapshot == 'before' ? locator : gate
        Throwable failure = null
        Throwable addFailure = null

        when: "it is added while the refresh is parked"
        Thread refresher = parkIn(parking) {
            try {
                refresh == 'refresh' ? env.refresh() : env.start()
            } catch (Throwable t) {
                failure = t
            }
        }
        changeWhileParked {
            try {
                env.addPropertySource(PropertySource.of('a', values, 50))
            } catch (Throwable t) {
                addFailure = t
            }
        }
        String addedDuringRefresh = env.getProperty('a.ok', String).orElse(null)
        String elementDuringRefresh = env.getProperty('foo[0]', String).orElse(null)
        release(parking, refresher)

        then: "the add is not thrown, and the property source stays registered: added before, it was only registered; added after, it was published at once, unless it failed"
        addFailure == null
        env.propertySources*.name.contains('a')
        addedDuringRefresh == duringAdded
        elementDuringRefresh == duringElement

        and: "the refresh has processed it in its order: added before, one that fails part way fails the refresh, and otherwise the registered one sets the element of a copy of the immutable list"
        failure?.getClass() == refreshFails
        env.getProperty('p.ok', String).orElse(null) == pOk
        env.getProperty('a.ok', String).orElse(null) == aOk
        env.getProperty('foo[0]', String).orElse(null) == element
        element == null || env.getProperty('foo', List).get() == [element]

        when: "the environment is refreshed again"
        Throwable refreshFailure = null
        try {
            env.refresh()
        } catch (Throwable t) {
            refreshFailure = t
        }

        then: "it reads it in its order again, and one that fails part way fails it, as on 4.10.31"
        refreshFailure?.getClass() == nextFails
        env.getProperty('p.ok', String).orElse(null) == pOk
        env.getProperty('a.ok', String).orElse(null) == aOk
        env.getProperty('foo[0]', String).orElse(null) == element
        element == null || env.getProperty('foo', List).get() == [element]

        cleanup:
        locator.release.countDown()
        gate.release.countDown()
        env.stop()

        where: "a start that fails on its first property source has published nothing"
        relativeToSnapshot | refresh   | kind                 | duringAdded | duringElement | refreshFails          | pOk  | aOk  | element | nextFails
        'after'            | 'start'   | 'fails part way'     | null        | 'x'           | null                  | 'p'  | null | 'x'     | NumberFormatException
        'after'            | 'refresh' | 'fails part way'     | null        | 'x'           | null                  | 'p'  | null | 'x'     | NumberFormatException
        'after'            | 'start'   | 'only sets the list' | 'a'         | 'a'           | null                  | 'p'  | 'a'  | 'x'     | null
        'after'            | 'refresh' | 'only sets the list' | 'a'         | 'a'           | null                  | 'p'  | 'a'  | 'x'     | null
        'before'           | 'start'   | 'fails part way'     | null        | null          | NumberFormatException | null | null | null    | NumberFormatException
        'before'           | 'refresh' | 'fails part way'     | null        | 'x'           | NumberFormatException | 'p'  | null | 'x'     | NumberFormatException
        'before'           | 'start'   | 'only sets the list' | null        | null          | null                  | 'p'  | 'a'  | 'x'     | null
        'before'           | 'refresh' | 'only sets the list' | null        | 'x'           | null                  | 'p'  | 'a'  | 'x'     | null
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/13915')
    void "a refreshAndDiff reports the change it has read, and of a property source added #relativeToSnapshot it takes the registered property sources only what putting it in its order changes"() {
        given: "a changed value, a registered property source that sets an element of a list, a locator that parks the refresh before it takes the registered property sources, and a gate that parks it after"
        Gate gate = new Gate()
        Locator locator = new Locator()
        Map<String, Object> data = ['app.value': 'a']
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(new MapPropertySource('data', data))
        env.addPropertySource(PropertySource.of('p', ['p.ok': 'p', 'foo[0]': 'x'], 100))
        env.addPropertySource(gate)
        env.start()
        Parking parking = relativeToSnapshot == 'before' ? locator : gate
        Map<String, Object> diff = null
        Throwable failure = null
        Throwable addFailure = null

        when: "a property source of a lower order that sets the list so that it cannot be changed is added while the refresh is parked"
        data.put('app.value', 'b')
        Thread refresher = parkIn(parking) {
            try {
                diff = env.refreshAndDiff()
            } catch (Throwable t) {
                failure = t
            }
        }
        changeWhileParked {
            try {
                env.addPropertySource(PropertySource.of('late', ['foo': List.of('a'), 'late.key': 'late'], 50))
            } catch (Throwable t) {
                addFailure = t
            }
        }
        String lateDuringRefresh = env.getProperty('late.key', String).orElse(null)
        String elementDuringRefresh = env.getProperty('foo[0]', String).orElse(null)
        String valueDuringRefresh = env.getProperty('app.value', String).orElse(null)
        release(parking, refresher)

        then: "added after, it was visible at once, on top of what was published, which the refresh had not changed yet; added before, it was only registered"
        failure == null
        addFailure == null
        lateDuringRefresh == duringLate
        elementDuringRefresh == duringElement
        valueDuringRefresh == 'a'

        and: "the refresh publishes it in its order, and reports its own change: the element the registered one sets back, but not the property source added after"
        valuesOf(diff) == reported
        env.getProperty('app.value', String).get() == 'b'
        env.getProperty('p.ok', String).get() == 'p'
        env.getProperty('late.key', String).get() == 'late'
        env.getProperty('foo[0]', String).get() == 'x'
        env.getProperty('foo', List).get() == ['x']

        when: "the environment is refreshed and diffed again"
        Map<String, Object> next = env.refreshAndDiff()

        then: "it reads the same, and reports no change"
        next == [:]
        env.getProperty('foo', List).get() == ['x']
        env.getProperty('late.key', String).get() == 'late'

        cleanup:
        gate.release.countDown()
        locator.release.countDown()
        env.stop()

        where:
        relativeToSnapshot | duringLate | duringElement | reported
        'after'            | 'late'     | 'a'           | ['app.value': 'a', 'foo[0]': 'a', 'foo': ['a']]
        'before'           | null       | 'x'           | ['app.value': 'a', 'late.key': 'late']
    }

    void "the difference of a refresh holds a change made before it takes the registered property sources, and not a property source added after, which the add has published itself: #change"() {
        given: "a locator that parks the refresh before it takes the registered property sources, and a gate that parks it after"
        Locator locator = new Locator()
        Gate gate = new Gate()
        Map<String, Object> data = ['app.value': 'a']
        PropertySource x = PropertySource.of('x', ['x.value': 'old', 'x.only': 'x'], 20)
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(new MapPropertySource('data', data))
        env.addPropertySource(x)
        env.addPropertySource(gate)
        env.start()
        Map<String, Object> diff = null

        when: "a property source is changed while the refresh is parked in the locator, and another is added while it is parked in the gate"
        data.put('app.value', 'b')
        Thread refresher = parkIn(locator) { diff = env.refreshAndDiff() }
        changeWhileParked {
            if (change == 'added') {
                env.addPropertySource(PropertySource.of('first', ['first.key': 'first']))
            } else if (change == 'replaced') {
                env.addPropertySource(PropertySource.of('x', ['x.value': 'new'], 20))
            } else {
                env.removePropertySource(x)
            }
        }
        boolean firstVisibleBeforeSnapshot = env.containsProperty('first.key')
        String xBeforeSnapshot = env.getProperty('x.value', String).orElse(null)
        List<String> registeredBeforeSnapshot = env.propertySources*.name
        gate.arm()
        locator.release.countDown()
        assert gate.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        changeWhileParked {
            env.addPropertySource(PropertySource.of('second', ['second.key': 'second']))
        }
        String secondAfterSnapshot = env.getProperty('second.key', String).orElse(null)
        release(gate, refresher)

        then: "the change made before was only registered, and the refresh reads it as part of its change"
        !firstVisibleBeforeSnapshot
        xBeforeSnapshot == 'old'
        registeredBeforeSnapshot.contains('first') == (change == 'added')
        registeredBeforeSnapshot.contains('x') == (change != 'removed')
        valuesOf(diff) == reported

        and: "the one added after was published by the add, and is kept, but is not reported as the refresh's change"
        secondAfterSnapshot == 'second'
        env.getProperty('second.key', String).get() == 'second'
        env.getProperty('app.value', String).get() == 'b'
        env.getProperty('first.key', String).orElse(null) == (change == 'added' ? 'first' : null)
        env.getProperty('x.value', String).orElse(null) == xAfter
        env.propertySources*.name.contains('second')

        cleanup:
        locator.release.countDown()
        gate.release.countDown()
        env.stop()

        where:
        change     | xAfter | reported
        'added'    | 'old'  | ['app.value': 'a', 'first.key': 'first']
        'replaced' | 'new'  | ['app.value': 'a', 'x.value': 'old', 'x.only': 'x']
        'removed'  | null   | ['app.value': 'a', 'x.value': 'old', 'x.only': 'x']
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/13914')
    void "a property source that sets an element of an immutable list another one has set, added while the environment refreshes, overrides it on a copy at once, and the refresh keeps it: #refresh"() {
        given: "a registered property source with a list that cannot be changed"
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('base', ['foo': List.of('a')]))
        env.addPropertySource(gate)
        if (refresh == 'refresh') {
            env.start()
        }
        Throwable failure = null
        Throwable addFailure = null

        when: "one that sets an element of it is added while the refresh is parked after the others"
        Thread refresher = parkIn(gate) {
            try {
                refresh == 'refresh' ? env.refresh() : env.start()
            } catch (Throwable t) {
                failure = t
            }
        }
        changeWhileParked {
            try {
                env.addPropertySource(PropertySource.of('overriding', ['overriding.key': 'overriding', 'foo[0]': 'x']))
            } catch (Throwable t) {
                addFailure = t
            }
        }
        String elementDuringRefresh = env.getProperty('foo[0]', String).orElse(null)
        String keyDuringRefresh = env.getProperty('overriding.key', String).orElse(null)
        release(gate, refresher)

        then: "the add does not fail, and is visible at once"
        addFailure == null
        elementDuringRefresh == 'x'
        keyDuringRefresh == 'overriding'

        and: "the refresh publishes it after the one of the same order it has read"
        failure == null
        env.getProperty('foo[0]', String).get() == 'x'
        env.getRequiredProperty('foo', List) == ['x']
        env.getProperty('overriding.key', String).get() == 'overriding'
        env.propertySources*.name.contains('overriding')

        when: "the environment is refreshed again"
        env.refresh()

        then: "the refresh reads it in its order, and does not fail on it"
        noExceptionThrown()
        env.getProperty('foo[0]', String).get() == 'x'
        env.getRequiredProperty('foo', List) == ['x']
        env.getProperty('overriding.key', String).get() == 'overriding'

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        refresh << ['start', 'refresh']
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/13914')
    void "a property source added while the environment refreshes, which writes into a registered one's nested list, is visible at once without that list being written, and the refresh publishes none of it once it #change: #refresh"() {
        given: "a registered property source with a list of maps"
        Gate gate = new Gate()
        List<Object> nested = new ArrayList<Object>([new LinkedHashMap<String, Object>(['a': '1'])])
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('a', ['foo': nested], 0))
        env.addPropertySource(gate)
        if (refresh == 'refresh') {
            env.start()
        }
        Map<String, Object> values = ['foo[0].c': 'leak', 'x.key': 'x']
        if (change == 'fails part way') {
            values['x.bad[99999999999]'] = 'y'
        }
        PropertySource x = PropertySource.of('x', values)
        PropertySource replacement = PropertySource.of('x', ['x.key': 'x2'], 10)
        Throwable failure = null

        when: "it is added while the refresh is parked after it has taken the registered property sources"
        Thread refresher = parkIn(gate) {
            try {
                refresh == 'refresh' ? env.refresh() : env.start()
            } catch (Throwable t) {
                failure = t
            }
        }
        changeWhileParked {
            env.addPropertySource(x)
        }
        String added = env.getProperty('foo[0].c', String).orElse(null)

        and: "it is removed or replaced while the refresh is still parked"
        changeWhileParked {
            if (change == 'is removed') {
                env.removePropertySource(x)
            } else if (change == 'is replaced') {
                env.addPropertySource(replacement)
            }
        }
        String changed = env.getProperty('foo[0].c', String).orElse(null)
        String keyDuringRefresh = env.getProperty('x.key', String).orElse(null)
        release(gate, refresher)

        then: "what it wrote was visible once it was added, unless it failed, and stayed until the refresh published"
        failure == null
        added == (change == 'fails part way' ? null : 'leak')
        changed == added
        keyDuringRefresh == (change == 'is removed' ? 'x' : (change == 'is replaced' ? 'x2' : null))

        and: "the refresh publishes none of it, and the registered list was never written"
        env.getProperty('foo[0].a', String).get() == '1'
        !env.getProperty('foo[0].c', String).present
        env.getProperty('x.key', String).orElse(null) == (change == 'is replaced' ? 'x2' : null)
        nested == [[a: '1']]

        and: "the registry holds what the change left: one that failed stays registered"
        change == 'is removed' ? !env.propertySources*.name.contains('x') : env.propertySources.find { it.name == 'x' }.is(change == 'is replaced' ? replacement : x)

        when: "the environment is refreshed again"
        Throwable refreshFailure = null
        try {
            env.refresh()
        } catch (Throwable t) {
            refreshFailure = t
        }

        then: "it reads what is registered: one that fails part way fails it"
        (refreshFailure instanceof NumberFormatException) == (change == 'fails part way')
        change == 'fails part way' || refreshFailure == null
        !env.getProperty('foo[0].c', String).present
        nested == [[a: '1']]

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        [change, refresh] << [['fails part way', 'is removed', 'is replaced'], ['start', 'refresh']].combinations()
    }

    void "a registered property source added again while the refresh is parked reading it is processed again at once on the adding thread, which does not wait for the refresh: #refresh"() {
        given:
        CountsReaders counting = new CountsReaders()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(counting)
        if (refresh == 'refresh') {
            env.start()
        }

        when: "it is added again while the refresh is parked reading it"
        Thread refresher = parkIn(counting) { refresh == 'refresh' ? env.refresh() : env.start() }
        changeWhileParked {
            env.addPropertySource(counting)
        }
        int overlapsWhileParked = counting.overlaps.get()
        release(counting, refresher)

        then: "the add read it while the refresh was reading it, rather than waiting for the refresh"
        overlapsWhileParked == 1
        counting.overlaps.get() == 1

        and: "the refresh publishes it, and the same instance stays registered"
        env.getProperty('counting.key', String).get() == 'counting'
        env.propertySources*.name == ['counting']
        env.propertySources.first().is(counting)

        cleanup:
        counting.release.countDown()
        env.stop()

        where:
        refresh << ['start', 'refresh']
    }

    void "a property source replaced during a refresh, by an add called after one that is still processing the property source it replaces, keeps the replacement, in the registry and in the properties: the refresh #outcome"() {
        given:
        AtomicBoolean fail = new AtomicBoolean()
        Gate gate = new Gate({
            if (fail.get()) {
                throw new IllegalStateException('failed to read the property source')
            }
        })
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(gate)
        env.start()
        ParksBeforeProcessing first = new ParksBeforeProcessing()
        PropertySource replacement = PropertySource.of('lower', ['lower.key': 'replacement'], -100)
        Throwable refreshFailure = null

        when: "it is added, then a refresh starts, and another thread replaces it during the refresh"
        first.arm()
        Thread adder = daemon('adder') { env.addPropertySource(first) }
        assert first.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        fail.set(outcome == 'fails')
        Thread refresher = parkIn(gate) {
            try {
                env.refresh()
            } catch (IllegalStateException e) {
                refreshFailure = e
            }
        }
        changeWhileParked {
            env.addPropertySource(replacement)
        }
        String afterReplacement = env.getProperty('lower.key', String).orElse(null)
        first.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        boolean addedWhileRefreshParked = !adder.alive
        String afterFirst = env.getProperty('lower.key', String).orElse(null)
        release(gate, refresher)

        then: "both adds return without waiting for the refresh; the add called later wins, so the first publishes nothing"
        afterReplacement == 'replacement'
        addedWhileRefreshParked
        afterFirst == 'replacement'
        (refreshFailure != null) == (outcome == 'fails')

        and: "the replacement stays, whether the refresh succeeds or fails"
        env.getProperty('lower.key', String).get() == 'replacement'
        !env.containsProperty('order.value')
        env.propertySources.find { it.name == 'lower' }.is(replacement)

        when:
        fail.set(false)
        env.refresh()

        then: "the next refresh reads the replacement"
        env.getProperty('lower.key', String).get() == 'replacement'
        !env.containsProperty('order.value')
        env.propertySources.find { it.name == 'lower' }.is(replacement)

        cleanup:
        gate.release.countDown()
        first.release.countDown()
        env.stop()

        where:
        outcome << ['succeeds', 'fails']
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/13914')
    void "a registered property source whose map an indexed key puts into itself does not fail a refresh that a property source is added during, and its map is not changed: #refresh"() {
        given:
        Gate gate = new Gate()
        HoldsItself holdsItself = new HoldsItself()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(holdsItself)
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)
        if (refresh != 'start') {
            env.start()
        }
        Map<String, Object> diff = null
        Throwable failure = null

        when: "a property source is added while the refresh is parked after it has taken the registered property sources"
        Thread refresher = parkIn(gate) {
            try {
                if (refresh == 'refreshAndDiff') {
                    diff = env.refreshAndDiff()
                } else {
                    refresh == 'refresh' ? env.refresh() : env.start()
                }
            } catch (Throwable t) {
                failure = t
            }
        }
        changeWhileParked {
            env.addPropertySource(PropertySource.of('late', ['late.key': 'late']))
        }
        String duringRefresh = env.getProperty('late.key', String).orElse(null)
        release(gate, refresher)

        then: "the added one was visible at once and is kept with what the refresh publishes"
        failure == null
        duringRefresh == 'late'
        env.getProperty('late.key', String).get() == 'late'
        env.getProperty('m.a', String).get() == '1'
        env.getProperty('app.value', String).get() == 'a'

        and: "no map the property source gave holds itself"
        !holdsItself.given.empty
        holdsItself.given.every { !it.containsKey('self') }

        and: "the difference compares the maps without failing, and holds nothing: the added one is in both"
        refresh != 'refreshAndDiff' || diff == [:]

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        refresh << ['start', 'refresh', 'refreshAndDiff']
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
     * The values of a difference. A key of a first letter that no property had before, or has no
     * more, is reported with its entry rather than its value, as on 5.2.x.
     */
    private static Map<String, Object> valuesOf(Map<String, Object> diff) {
        diff.collectEntries { String key, Object value ->
            [(key): value instanceof DefaultPropertyEntry ? ((DefaultPropertyEntry) value).value() : value]
        } as Map<String, Object>
    }

    /**
     * Runs the refresh on another thread, once the parking is armed, and returns that thread once
     * the refresh is parked.
     */
    private static Thread parkIn(Parking parking, Closure<?> refresh) {
        parking.arm()
        Thread refresher = daemon('refresher', refresh)
        assert parking.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        return refresher
    }

    /**
     * Runs the change on another thread, which must finish while the refresh is parked.
     */
    private static void changeWhileParked(Closure<?> change) {
        Thread changer = daemon('changer', change)
        changer.join(WAIT_SECONDS * 1000L)
        assert !changer.alive
    }

    private static void release(Parking parking, Thread refresher) {
        parking.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)
        assert !refresher.alive
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
                if (!release.await(AddAfterSnapshotSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the parked thread was never released')
                }
                return true
            }
            return false
        }
    }

    /**
     * A property sources locator that parks the refresh, once armed, and returns the given property
     * sources.
     */
    static class Locator extends Parking implements PropertySourcesLocator {
        private final List<PropertySource> sources

        Locator(PropertySource... sources) {
            this.sources = sources as List
        }

        @Override
        Collection<PropertySource> load(Environment environment) {
            pass()
            return sources
        }
    }

    /**
     * A property source without properties, processed after the others, that parks the thread that
     * iterates it, once armed, and then runs the given action.
     */
    static class Gate extends Parking implements PropertySource {
        private final Runnable afterRelease

        Gate(Runnable afterRelease = {}) {
            this.afterRelease = afterRelease
        }

        @Override
        String getName() { 'gate' }

        @Override
        int getOrder() { Ordered.LOWEST_PRECEDENCE }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (pass()) {
                afterRelease.run()
            }
            return Collections.emptyIterator()
        }
    }

    /**
     * A property source with the given properties that parks the thread that iterates it, once
     * armed.
     */
    static class ParkingPropertySource extends Parking implements PropertySource {
        private final String name
        private final Map<String, Object> values

        ParkingPropertySource(String name, Map<String, Object> values) {
            this.name = name
            this.values = values
        }

        @Override
        String getName() { name }

        @Override
        Object get(String key) { values.get(key) }

        @Override
        Iterator<String> iterator() {
            pass()
            return values.keySet().iterator()
        }
    }

    /**
     * A property source of a lower order that, once armed, parks the thread that asks for its
     * convention, which the environment does before it processes it.
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
     * A property source that, each time it is iterated, has another thread add a property source
     * to the environment, and waits for it a bounded time.
     */
    static class WaitsForAddEachTime implements PropertySource {
        private final Environment env
        private final AtomicInteger iterations = new AtomicInteger()
        final List<Boolean> addedInTime = new CopyOnWriteArrayList<>()

        WaitsForAddEachTime(Environment env) {
            this.env = env
        }

        @Override
        String getName() { 'waits' }

        @Override
        Object get(String key) { key == 'waits.key' ? 'waits' : null }

        @Override
        Iterator<String> iterator() {
            int i = iterations.incrementAndGet()
            Thread other = new Thread({ env.addPropertySource(PropertySource.of('other' + i, ['other.key': 'other'])) })
            other.daemon = true
            other.start()
            other.join(3_000)
            addedInTime << !other.alive
            return ['waits.key'].iterator()
        }
    }

    /**
     * A property source that adds, on the thread that iterates it, another one each time it is
     * iterated.
     */
    static class AddsEachTime implements PropertySource {
        private final Environment env
        private final AtomicInteger iterations = new AtomicInteger()

        AddsEachTime(Environment env) {
            this.env = env
        }

        @Override
        String getName() { 'adding' }

        @Override
        Object get(String key) { key == 'adding.key' ? 'adding' : null }

        @Override
        Iterator<String> iterator() {
            env.addPropertySource(PropertySource.of('nested', ['nested.key': 'n' + iterations.incrementAndGet()]))
            return ['adding.key'].iterator()
        }
    }

    /**
     * A property source that, once armed, parks the thread that iterates it, and counts the threads
     * that iterate it while another one does.
     */
    static class CountsReaders extends Parking implements PropertySource {
        final AtomicInteger inside = new AtomicInteger()
        final AtomicInteger overlaps = new AtomicInteger()

        @Override
        String getName() { 'counting' }

        @Override
        Object get(String key) { key == 'counting.key' ? 'counting' : null }

        @Override
        Iterator<String> iterator() {
            if (inside.incrementAndGet() > 1) {
                overlaps.incrementAndGet()
            }
            try {
                pass()
                return ['counting.key'].iterator()
            } finally {
                inside.decrementAndGet()
            }
        }
    }

    /**
     * A property source that gives, each time it is read, a new map under two keys, one of which
     * makes processing put the map into itself, and keeps every map it has given.
     */
    static class HoldsItself implements PropertySource {
        final List<Map<String, Object>> given = new CopyOnWriteArrayList<>()
        private volatile Map<String, Object> current = new LinkedHashMap<String, Object>(['a': '1'])

        @Override
        String getName() { 'holds-itself' }

        @Override
        Object get(String key) { key == 'm' || key == 'm[self]' ? current : null }

        @Override
        Iterator<String> iterator() {
            current = new LinkedHashMap<String, Object>(['a': '1'])
            given.add(current)
            return ['m', 'm[self]'].iterator()
        }
    }
}

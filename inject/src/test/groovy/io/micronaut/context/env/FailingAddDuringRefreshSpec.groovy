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
import java.util.concurrent.atomic.AtomicReference

/**
 * Property sources that fail when they are added while a start or a refresh reads the property
 * sources, and adds made while a refresh fails. An add that fails publishes nothing and leaves its
 * property source registered; while a refresh is in progress its failure is logged rather than
 * thrown, and the refresh is not failed by it. A refresh that fails publishes nothing more and takes
 * back nothing an add has published. The threads are parked at fixed points, and every wait is
 * bounded.
 */
@Timeout(60)
class FailingAddDuringRefreshSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "a property source that fails, added while a #refresh processes the property sources and added again, fails neither the adds nor the refresh, publishes nothing, stays registered, and the next refresh fails on it: #source.name"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)
        if (refresh == 'refresh') {
            env.start()
        }
        Throwable failure = null
        List<Throwable> addFailures = Collections.synchronizedList([])
        Closure<?> add = { PropertySource added ->
            try {
                env.addPropertySource(added)
            } catch (Throwable t) {
                addFailures << t
            }
        }

        when: "another thread adds it while the refresh is parked"
        Thread refresher = parkIn(gate) {
            try {
                refresh == 'refresh' ? env.refresh() : env.start()
            } catch (Throwable t) {
                failure = t
            }
        }
        changeWhileParked {
            add(source)
        }
        boolean anyOfItVisible = env.containsProperty('partial.ok') || env.containsProperty('foo')

        and: "then one that sets an element of a list the failing one may have set, and the failing one again"
        changeWhileParked {
            add(PropertySource.of('late', ['late.key': 'late', 'foo[0]': 'x']))
            add(source)
        }
        String lateDuringRefresh = env.getProperty('late.key', String).orElse(null)
        List<Object> fooDuringRefresh = env.getProperty('foo', List).orElse(null)
        release(gate, refresher)

        then: "each failure is only logged, as a refresh is in progress, nothing of the failing one is published, and the other was visible at once"
        addFailures.empty
        failure == null
        !anyOfItVisible
        lateDuringRefresh == 'late'
        fooDuringRefresh == ['x']

        and: "the refresh publishes what it has read with the other one"
        env.getProperty('app.value', String).get() == 'a'
        env.getProperty('late.key', String).get() == 'late'
        env.getProperty('foo', List).get() == ['x']
        !env.containsProperty('partial.ok')

        and: "it stays registered, in the order it was first added"
        env.propertySources*.name == ['data', 'gate', source.name, 'late']

        and: "a property source added afterwards is processed at once"
        env.addPropertySource(PropertySource.of('after', ['after.key': 'after'])).getProperty('after.key', String).get() == 'after'

        when:
        env.refresh()

        then: "the next refresh reads it and fails on it, as a refresh fails on a registered property source"
        Throwable e = thrown()
        expected.isInstance(e)
        message == null || e.message == message

        and: "the failed refresh publishes nothing and leaves the registered property sources as they were"
        env.getProperty('late.key', String).get() == 'late'
        env.getProperty('after.key', String).get() == 'after'
        env.propertySources*.name == ['data', 'gate', source.name, 'late', 'after']

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        refresh   | source                                    | expected                  | message
        'start'   | new FailingPropertySource()               | IllegalArgumentException  | 'failed to iterate the property source'
        'refresh' | new FailingPropertySource()               | IllegalArgumentException  | 'failed to iterate the property source'
        'start'   | new ErrorPropertySource()                 | NoClassDefFoundError      | 'a class the property source needs'
        'refresh' | new ErrorPropertySource()                 | NoClassDefFoundError      | 'a class the property source needs'
        'start'   | ThrowsWhenIterated.checked()              | IOException               | 'failed to read the file'
        'refresh' | ThrowsWhenIterated.checked()              | IOException               | 'failed to read the file'
        'start'   | ThrowsWhenIterated.assertion()            | AssertionError            | 'an assertion of the property source'
        'refresh' | ThrowsWhenIterated.assertion()            | AssertionError            | 'an assertion of the property source'
        'start'   | ThrowsWhenIterated.serviceConfiguration() | ServiceConfigurationError | 'a provider could not be loaded'
        'refresh' | ThrowsWhenIterated.serviceConfiguration() | ServiceConfigurationError | 'a provider could not be loaded'
        'start'   | ThrowsWhenIterated.stackOverflow()        | StackOverflowError        | 'the property source recursed'
        'refresh' | ThrowsWhenIterated.stackOverflow()        | StackOverflowError        | 'the property source recursed'
        'start'   | new Unordered()                           | IllegalStateException     | 'no order'
        'refresh' | new Unordered()                           | IllegalStateException     | 'no order'
        'start'   | failsPartWay()                            | NumberFormatException     | null
        'refresh' | failsPartWay()                            | NumberFormatException     | null
    }

    void "a registered property source added again while a #refresh is in progress, when it now #kind, fails neither the add nor the refresh, the refresh keeps what it read of it, and the next refresh fails on it"() {
        given:
        Gate gate = new Gate()
        FailsOrChanges source = new FailsOrChanges(['m.value': 'v1'])
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(source)
        env.addPropertySource(gate)
        if (refresh == 'refresh') {
            env.start()
        }
        Throwable failure = null
        Throwable addFailure = null

        when: "it is changed so that it fails, and added again, once the refresh has read it"
        Thread refresher = parkIn(gate) {
            try {
                refresh == 'refresh' ? env.refresh() : env.start()
            } catch (Throwable t) {
                failure = t
            }
        }
        changeWhileParked {
            if (kind == 'fails to be read') {
                source.fail.set(true)
            } else {
                source.values.put('m.value', 'v2')
                source.values.put('m.extra', 'extra')
                source.values.put('m.bad[99999999999]', 'x')
            }
            try {
                env.addPropertySource(source)
            } catch (Throwable t) {
                addFailure = t
            }
        }
        int iterationsAfterAdd = source.iterations.get()
        String duringRefresh = env.getProperty('m.value', String).orElse(null)
        release(gate, refresher)

        then: "the add read it again and failed, which is only logged, and published nothing"
        addFailure == null
        iterationsAfterAdd == (refresh == 'start' ? 2 : 3)
        duringRefresh == 'v1'

        and: "the refresh does not fail on it, and publishes exactly what it read of it before"
        failure == null
        env.getProperty('m.value', String).get() == 'v1'
        !env.getProperty('m.extra', String).present
        env.propertySources.find { it.name == 'mutable' }.is(source)

        when:
        env.refresh()

        then: "the next refresh fails on it, as before, and publishes nothing"
        thrown(expected)
        env.getProperty('m.value', String).get() == 'v1'
        !env.getProperty('m.extra', String).present

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        refresh   | kind                    | expected
        'start'   | 'fails to be read'      | IllegalArgumentException
        'refresh' | 'fails to be read'      | IllegalArgumentException
        'start'   | 'fails to be processed' | NumberFormatException
        'refresh' | 'fails to be processed' | NumberFormatException
    }

    void "a registered property source replaced, after a refresh has taken the registered property sources, by one that fails keeps its values until the next refresh, which fails on the replacement: #refresh, #kind"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'v1'], 10))
        env.addPropertySource(gate)
        if (refresh == 'refresh') {
            env.start()
        }
        PropertySource replacement = kind == 'fails to be read'
            ? new ThrowsWhenIterated('data', new IllegalArgumentException('failed to iterate the property source'))
            : PropertySource.of('data', ['app.value': 'v2', 'app.bad[99999999999]': 'x'], 10)
        Throwable failure = null
        Throwable addFailure = null

        when: "it is replaced while the refresh is parked in the gate, after it has read the replaced one"
        Thread refresher = parkIn(gate) {
            try {
                refresh == 'refresh' ? env.refresh() : env.start()
            } catch (Throwable t) {
                failure = t
            }
        }
        changeWhileParked {
            try {
                env.addPropertySource(replacement)
            } catch (Throwable t) {
                addFailure = t
            }
        }
        String duringRefresh = env.getProperty('app.value', String).orElse(null)
        release(gate, refresher)

        then: "the add is logged rather than thrown, as a refresh was in progress, and publishes nothing"
        addFailure == null
        duringRefresh == 'v1'

        and: "the refresh publishes what it read from the replaced one, and the replacement is registered by its name"
        failure == null
        env.getProperty('app.value', String).get() == 'v1'
        env.propertySources.find { it.name == 'data' }.is(replacement)

        when: "the environment is refreshed again"
        env.refresh()

        then: "it reads the replacement and fails on it, as on 4.10.31, and the published properties stay"
        thrown(expected)
        env.getProperty('app.value', String).get() == 'v1'
        env.propertySources.find { it.name == 'data' }.is(replacement)

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        refresh   | kind                    | expected
        'start'   | 'fails to be read'      | IllegalArgumentException
        'refresh' | 'fails to be read'      | IllegalArgumentException
        'start'   | 'fails to be processed' | NumberFormatException
        'refresh' | 'fails to be processed' | NumberFormatException
    }

    void "a property source that a bootstrap locator returns, replaced during a refresh by one that fails to be processed, keeps what the locator returned, and the next refresh registers what the locator returns again"() {
        given: "a bootstrap locator that returns a new property source each time it loads"
        Gate gate = new Gate()
        ConsulLikeLocator locator = new ConsulLikeLocator({ int load -> [PropertySource.of('consul', ['consul.value': 'v' + load], 50)] })
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(gate)
        env.start()
        PropertySource replacement = PropertySource.of('consul', ['consul.value': 'v9', 'consul.bad[99999999999]': 'x'], 50)
        Throwable failure = null
        Throwable addFailure = null

        when: "it is replaced while the refresh is parked once it has read what the locator returns"
        Thread refresher = parkIn(gate) {
            try {
                env.refresh()
            } catch (Throwable t) {
                failure = t
            }
        }
        changeWhileParked {
            try {
                env.addPropertySource(replacement)
            } catch (Throwable t) {
                addFailure = t
            }
        }
        String duringRefresh = env.getProperty('consul.value', String).orElse(null)
        release(gate, refresher)

        then: "the add is logged rather than thrown, and nothing of the replacement is published"
        failure == null
        addFailure == null
        duringRefresh == 'v1'

        and: "the refresh publishes what the locator returned it, and leaves the replacement registered rather than what it loaded itself"
        env.getProperty('consul.value', String).get() == 'v2'
        env.propertySources.find { it.name == 'consul' }.is(replacement)

        when: "the environment is refreshed again"
        env.refresh()

        then: "it replaces the property source registered by that name, as 5.2.x did: it does not read the replacement, and registers what the locator returns"
        env.getProperty('consul.value', String).get() == 'v3'
        !env.propertySources.find { it.name == 'consul' }.is(replacement)

        when: "the replacement is removed and the environment refreshed"
        env.removePropertySource(replacement)
        env.refresh()

        then: "what the locator returns is read and registered again"
        env.getProperty('consul.value', String).get() == 'v4'
        env.propertySources.find { it.name == 'consul' }.get('consul.value') == 'v4'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source that fails to be read and adds #how when it is read, added while the environment refreshes, is read by the thread adding it and not by the refresh, and the add called last stays registered: #refresh"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)
        if (refresh == 'refresh') {
            env.start()
        }
        // re-adds a new instance, nested, until it has been read three times: unbounded, the nesting
        // would go on until the stack of the adding thread overflows, as it does outside a refresh
        ReAddsWhenRead source = new ReAddsWhenRead(env, how == 'a new instance by its name', 3)
        Throwable failure = null
        Throwable addFailure = null

        when: "it is added while the refresh is parked"
        Thread refresher = parkIn(gate) {
            try {
                refresh == 'refresh' ? env.refresh() : env.start()
            } catch (Throwable t) {
                failure = t
            }
        }
        changeWhileParked {
            try {
                env.addPropertySource(source)
            } catch (Throwable t) {
                addFailure = t
            }
        }
        int readsByAdd = source.reads.get()
        release(gate, refresher)

        then: "the adding thread read it, and failed, which is only logged: adding the instance being added is no change, and of nested adds by its name the one called last is registered"
        addFailure == null
        readsByAdd == (how == 'itself again' ? 1 : 3)
        env.propertySources.find { it.name == 'readds' }.is(how == 'itself again' ? source : source.last.get())

        and: "the refresh finished without reading it, and published"
        failure == null
        source.reads.get() == readsByAdd
        env.getProperty('app.value', String).get() == 'a'

        when: "the environment is refreshed again"
        env.refresh()

        then: "the refresh reads it in its order, and fails on it"
        thrown(IllegalStateException)
        source.reads.get() == (how == 'itself again' ? 3 : 4)
        env.getProperty('app.value', String).get() == 'a'

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        [refresh, how] << [['start', 'refresh'], ['itself again', 'a new instance by its name']].combinations()
    }

    void "a property source that fails in a way that cannot be described, added while the environment refreshes, does not fail the refresh, and the add only logs the failure and registers it: #how, #refresh"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)
        if (refresh == 'refresh') {
            env.start()
        }
        PropertySource added = how == 'its message fails' ? new FailsWithoutMessage('bad-message') : new NameFailsLater('late-name', 3)
        Throwable failure = null
        Throwable addFailure = null

        when: "it is added while the refresh is parked after it has taken the registered property sources"
        Thread refresher = parkIn(gate) {
            try {
                refresh == 'refresh' ? env.refresh() : env.start()
            } catch (Throwable t) {
                failure = t
            }
        }
        changeWhileParked {
            try {
                env.addPropertySource(added)
            } catch (Throwable t) {
                addFailure = t
            }
        }
        release(gate, refresher)

        then: "the refresh publishes what it has read"
        failure == null
        env.getProperty('app.value', String).get() == 'a'

        and: "the add, made while a refresh is in progress, does not throw, and the property source stays registered"
        addFailure == null
        env.propertySources.any { it.is(added) }

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        [how, refresh] << [['its message fails', 'its name fails once read'], ['start', 'refresh']].combinations()
    }

    void "a property source added while a refresh that fails runs is visible at once and stays visible and registered, and one removed stays removed with its properties, until the next refresh: #failure"() {
        given: "a property source that, once armed, parks the refresh and then fails it"
        AtomicBoolean fail = new AtomicBoolean()
        Throwable readFailure = failure
        Gate gate = new Gate({
            if (fail.get()) {
                throw readFailure
            }
        })
        Map<String, Object> data = ['app.value': 'a']
        PropertySource extra = PropertySource.of('extra', ['extra.key': 'extra'])
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        env.addPropertySource(extra)
        env.addPropertySource(gate)
        env.start()

        when: "a property source is added and another removed during a refresh, and the refresh then fails"
        data.put('app.value', 'b')
        fail.set(true)
        Throwable refreshFailure = null
        Thread refresher = parkIn(gate) {
            try {
                env.refresh()
            } catch (Throwable t) {
                refreshFailure = t
            }
        }
        changeWhileParked {
            env.addPropertySource(PropertySource.of('late', ['late.key': 'late']))
            env.removePropertySource(extra)
        }
        String lateDuringRefresh = env.getProperty('late.key', String).orElse(null)
        release(gate, refresher)

        then: "the property source added meanwhile was visible at once"
        lateDuringRefresh == 'late'

        and: "the refresh fails and publishes nothing it read, and does not take back what the add published"
        refreshFailure.is(failure)
        env.getProperty('app.value', String).get() == 'a'
        env.getProperty('late.key', String).get() == 'late'

        and: "the keys of the property source removed meanwhile stay until the next refresh"
        env.getProperty('extra.key', String).get() == 'extra'

        and: "the property source added meanwhile is registered, and the one removed meanwhile stays removed"
        env.propertySources*.name.contains('late')
        !env.propertySources*.name.contains('extra')

        when: "the next refresh succeeds"
        fail.set(false)
        env.refresh()

        then:
        env.getProperty('app.value', String).get() == 'b'
        env.getProperty('late.key', String).get() == 'late'
        !env.getProperty('extra.key', String).present

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        failure << [new IllegalStateException('failed to read the property source'),
                    new AssertionError((Object) 'failed to read the property source')]
    }

    void "a property source that fails to be read, added while a refresh that fails runs, is logged and stays registered, and the refresh fails with its own failure: #source.name"() {
        given:
        IllegalStateException readFailure = new IllegalStateException('failed to read the property source')
        AtomicBoolean fail = new AtomicBoolean()
        Gate gate = new Gate({
            if (fail.get()) {
                throw readFailure
            }
        })
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)
        env.start()
        Throwable refreshFailure = null
        Throwable addFailure = null

        when: "one that fails to be read and one that can be read are added during a refresh, and the refresh then fails"
        fail.set(true)
        Thread refresher = parkIn(gate) {
            try {
                env.refresh()
            } catch (Throwable t) {
                refreshFailure = t
            }
        }
        changeWhileParked {
            try {
                env.addPropertySource(source)
            } catch (Throwable t) {
                addFailure = t
            }
            env.addPropertySource(PropertySource.of('late', ['late.key': 'late'], 100))
        }
        String lateDuringRefresh = env.getProperty('late.key', String).orElse(null)
        release(gate, refresher)

        then: "the add that fails is logged rather than thrown, as a refresh was in progress, and the other was visible at once"
        addFailure == null
        lateDuringRefresh == 'late'

        and: "the refresh's own failure is thrown, and both stay registered"
        refreshFailure.is(readFailure)
        refreshFailure.suppressed.length == 0
        env.propertySources*.name.containsAll([source.name, 'late'])

        and: "the failed refresh publishes nothing it read, and does not take back what the add published"
        env.getProperty('late.key', String).get() == 'late'
        env.getProperty('app.value', String).get() == 'a'

        and: "a property source added afterwards is processed"
        env.addPropertySource(PropertySource.of('after', ['after.key': 'after'])).getProperty('after.key', String).get() == 'after'

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        source << [new FailingPropertySource(), new ErrorPropertySource(), ThrowsWhenIterated.checked(), ThrowsWhenIterated.assertion()]
    }

    /**
     * A property source that sets a list that cannot be changed, and then fails to be processed, as
     * the index of its last property is too large for a list.
     */
    private static PropertySource failsPartWay() {
        PropertySource.of('partial', ['foo': List.of('a'), 'partial.ok': 'ok', 'partial.bad[99999999999]': 'x'])
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
                if (!release.await(FailingAddDuringRefreshSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the parked thread was never released')
                }
                return true
            }
            return false
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
     * A bootstrap locator, as the Consul configuration client is, that returns new property sources
     * each time it loads them.
     */
    static class ConsulLikeLocator implements PropertySourcesLocator, BootstrapLocatorMarker {
        private final Closure<List<PropertySource>> sources
        private final AtomicInteger loads = new AtomicInteger()

        ConsulLikeLocator(Closure<List<PropertySource>> sources) {
            this.sources = sources
        }

        @Override
        Collection<PropertySource> load(Environment environment) {
            return sources.call(loads.incrementAndGet())
        }
    }

    /**
     * A property source that throws an error when it is iterated, as one that misses a class does.
     */
    static class ErrorPropertySource implements PropertySource {
        @Override
        String getName() { 'error' }

        @Override
        int getOrder() { 50 }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            throw new NoClassDefFoundError('a class the property source needs')
        }
    }

    /**
     * A property source that fails to be iterated.
     */
    static class FailingPropertySource implements PropertySource {
        @Override
        String getName() { 'failing' }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            throw new IllegalArgumentException('failed to iterate the property source')
        }
    }

    /**
     * A property source that throws what it is given when it is iterated, as one written in Groovy or
     * Kotlin can throw a checked exception.
     */
    static class ThrowsWhenIterated implements PropertySource {
        private final String name
        private final Throwable failure

        ThrowsWhenIterated(String name, Throwable failure) {
            this.name = name
            this.failure = failure
        }

        static ThrowsWhenIterated checked() {
            new ThrowsWhenIterated('checked', new IOException('failed to read the file'))
        }

        static ThrowsWhenIterated assertion() {
            new ThrowsWhenIterated('assertion', new AssertionError((Object) 'an assertion of the property source'))
        }

        static ThrowsWhenIterated serviceConfiguration() {
            new ThrowsWhenIterated('service', new ServiceConfigurationError('a provider could not be loaded'))
        }

        static ThrowsWhenIterated stackOverflow() {
            new ThrowsWhenIterated('recursive', new StackOverflowError('the property source recursed'))
        }

        @Override
        String getName() { name }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            throw failure
        }
    }

    /**
     * A property source that fails when it is asked for its order.
     */
    static class Unordered implements PropertySource {
        @Override
        String getName() { 'unordered' }

        @Override
        int getOrder() { throw new IllegalStateException('no order') }

        @Override
        Object get(String key) { key == 'unordered.key' ? 'unordered' : null }

        @Override
        Iterator<String> iterator() { ['unordered.key'].iterator() }
    }

    /**
     * A property source with the given values that counts how often it is iterated, and fails to be
     * iterated once it is asked to.
     */
    static class FailsOrChanges implements PropertySource {
        final AtomicBoolean fail = new AtomicBoolean()
        final AtomicInteger iterations = new AtomicInteger()
        final Map<String, Object> values

        FailsOrChanges(Map<String, Object> values) {
            this.values = new LinkedHashMap<>(values)
        }

        @Override
        String getName() { 'mutable' }

        @Override
        Object get(String key) { values.get(key) }

        @Override
        Iterator<String> iterator() {
            iterations.incrementAndGet()
            if (fail.get()) {
                throw new IllegalArgumentException('failed to iterate the property source')
            }
            return new ArrayList<>(values.keySet()).iterator()
        }
    }

    /**
     * A property source that fails each time it is iterated and, while it has been read fewer times
     * than the given limit, first adds itself, or a new instance by its name, again.
     */
    static class ReAddsWhenRead implements PropertySource {
        final AtomicInteger reads
        final AtomicReference<PropertySource> last
        private final Environment env
        private final boolean newInstance
        private final int limit

        ReAddsWhenRead(Environment env, boolean newInstance, int limit, AtomicInteger reads = new AtomicInteger(), AtomicReference<PropertySource> last = new AtomicReference<>()) {
            this.env = env
            this.newInstance = newInstance
            this.limit = limit
            this.reads = reads
            this.last = last
        }

        @Override
        String getName() { 'readds' }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (reads.incrementAndGet() < limit) {
                PropertySource next = newInstance ? new ReAddsWhenRead(env, true, limit, reads, last) : this
                last.set(next)
                env.addPropertySource(next)
            }
            throw new IllegalStateException('the remote configuration is unreachable')
        }
    }

    /**
     * A property source that fails to be iterated with an exception whose message fails.
     */
    static class FailsWithoutMessage implements PropertySource {
        private final String name

        FailsWithoutMessage(String name) {
            this.name = name
        }

        @Override
        String getName() { name }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            throw new RuntimeException((String) null) {
                @Override
                String getMessage() {
                    throw new IllegalStateException('the message failed')
                }
            }
        }
    }

    /**
     * A property source whose name fails from the given call on, and that fails to be iterated.
     */
    static class NameFailsLater implements PropertySource {
        private final String name
        private final int failFrom
        private final AtomicInteger calls = new AtomicInteger()

        NameFailsLater(String name, int failFrom) {
            this.name = name
            this.failFrom = failFrom
        }

        @Override
        String getName() {
            if (calls.incrementAndGet() >= failFrom) {
                throw new IllegalStateException('the name failed')
            }
            return name
        }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            throw new IllegalStateException('failed to read the property source')
        }
    }
}

package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.core.order.Ordered
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Property sources added, replaced or removed while a start or a refresh loads the configuration
 * files or runs the locators, before it takes the registered property sources. Such a change is
 * only registered, and the read processes what is registered then in its order, as 4.10.31 did.
 * The threads are parked at fixed points, and every wait is bounded.
 */
@Timeout(60)
class AddBeforeInputCutSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/14138')
    void "a property source added while a #read loads the configuration files is only registered, is read by it in its order, and stays registered for the next refresh"() {
        given: "a configuration that, once armed, parks the read while it loads the default property sources, as the issue's reproducer does"
        Parking park = new Parking()
        DefaultEnvironment env = new DefaultEnvironment(parkingInDefaults(park))
        env.addPropertySource(PropertySource.of('higher', ['order.value': 'higher'], 100))
        if (read == 'refresh') {
            env.start()
        }

        when: "another thread adds a property source of a lower order that sets the same key, while the read is parked"
        Thread reader = parkIn(park) { read == 'refresh' ? env.refresh() : env.start() }
        changeWhileParked {
            env.addPropertySource(PropertySource.of('lower', ['order.value': 'lower', 'lower.key': 'lower'], -100))
        }
        boolean visibleBeforeRead = env.containsProperty('lower.key')
        boolean registeredBeforeRead = env.propertySources*.name.contains('lower')
        release(park, reader)

        then: "it is only registered, and the read processes it once, in its order, so the property source of the higher order keeps the key"
        !visibleBeforeRead
        registeredBeforeRead
        env.getProperty('lower.key', String).get() == 'lower'
        env.getProperty('order.value', String).get() == 'higher'
        env.propertySources*.name.contains('lower')

        when: "the environment is refreshed again"
        env.refresh()

        then: "it stays registered, as a property source that is added does, rather than being dropped by the next refresh"
        env.getProperty('lower.key', String).get() == 'lower'
        env.getProperty('order.value', String).get() == 'higher'
        env.propertySources*.name.contains('lower')

        cleanup:
        park.release.countDown()
        env.stop()

        where:
        read << ['start', 'refresh']
    }

    void "a property source that a property sources locator adds while the environment reads is processed by that read in its order, and by every refresh"() {
        given: "a locator that adds a new property source to the environment each time, rather than returning it"
        List<PropertySource> added = new CopyOnWriteArrayList<>()
        List<Boolean> inViewOfLocator = new CopyOnWriteArrayList<>()
        PropertySourcesLocator locator = new PropertySourcesLocator() {
            @Override
            Collection<PropertySource> load(Environment environment) {
                PropertySource located = PropertySource.of('located', ['located.key': 'located' + (added.size() + 1), 'shared.key': 'located'], -100)
                added << located
                environment.addPropertySource(located)
                inViewOfLocator << environment.propertySources.any { it.is(located) }
                return []
            }
        }
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(PropertySource.of('data', ['shared.key': 'data'], 100))

        when:
        env.start()

        then: "it is not left for the next refresh to read, and it is processed in its order, not on top"
        env.getProperty('located.key', String).get() == 'located1'
        env.getProperty('shared.key', String).get() == 'data'
        inViewOfLocator == [true]
        env.propertySources.find { it.name == 'located' }.is(added[0])

        when:
        env.refresh()

        then: "the refresh processes the one the locator has added during it"
        env.getProperty('located.key', String).get() == 'located2'
        env.getProperty('shared.key', String).get() == 'data'
        inViewOfLocator == [true, true]
        env.propertySources.find { it.name == 'located' }.is(added[1])

        cleanup:
        env.stop()
    }

    void "a property source whose value comes from the environment is given the value of a change made while the read loads the configuration files, and the next refresh keeps the change: #change, #read"() {
        given: "a property source whose value is computed from another property each time it is read"
        Parking park = new Parking()
        DefaultEnvironment env = new DefaultEnvironment(parkingInDefaults(park))
        PropertySource base = PropertySource.of('base', ['base.url': 'old'], 0)
        PropertySource replacement = PropertySource.of('base', ['base.url': 'new'], 0)
        if (change != 'added') {
            env.addPropertySource(base)
        }
        env.addPropertySource(new CopiesProperty(env, 'base.url', 'derived.url', 100))
        if (read == 'refresh') {
            env.start()
        }

        when: "what it is computed from changes while the read is parked where it reads the default property sources"
        Thread reader = parkIn(park) { read == 'refresh' ? env.refresh() : env.start() }
        changeWhileParked {
            if (change == 'removed') {
                env.removePropertySource(base)
            } else {
                env.addPropertySource(replacement)
            }
        }
        String beforeCut = env.getProperty('base.url', String).orElse(null)
        release(park, reader)
        Map<String, Object> published = [base: env.getProperty('base.url', String).orElse(null), derived: env.getProperty('derived.url', String).orElse(null)]

        then: "the change is only registered until the read takes the registered property sources"
        beforeCut == (read == 'refresh' && change != 'added' ? 'old' : null)

        and: "the read processes it in its order, so the value computed from it is the changed one"
        published == [base: change == 'removed' ? null : 'new', derived: change == 'removed' ? null : 'new']
        change == 'removed' ? !env.propertySources*.name.contains('base') : env.propertySources.find { it.name == 'base' }.is(replacement)

        and: "the next refresh keeps the change"
        env.refresh().getProperty('derived.url', String).orElse(null) == published.derived
        env.getProperty('base.url', String).orElse(null) == published.base

        cleanup:
        park.release.countDown()
        env.stop()

        where:
        [change, read] << [['added', 'replaced', 'removed'], ['start', 'refresh']].combinations()
    }

    void "a registered property source that fails now, removed or replaced while the read loads the configuration files, is not read and does not fail the read: #fails, #change, #read"() {
        given:
        Parking park = new Parking()
        DefaultEnvironment env = new DefaultEnvironment(parkingInDefaults(park))
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        FailsWhenSet flaky = new FailsWhenSet(fails)
        env.addPropertySource(flaky)
        if (read == 'refresh') {
            env.start()
        }
        flaky.fail.set(true)
        PropertySource replacement = PropertySource.of('flaky', ['flaky.key': 'new'], 0)
        Throwable failure = null

        when: "it is removed or replaced while the read is parked where it reads the default property sources"
        Thread reader = parkIn(park) {
            try {
                read == 'refresh' ? env.refresh() : env.start()
            } catch (Throwable t) {
                failure = t
            }
        }
        changeWhileParked {
            if (change == 'removed') {
                env.removePropertySource(flaky)
            } else {
                env.addPropertySource(replacement)
            }
        }
        release(park, reader)

        then: "the read does not read it, as it takes the registered property sources once it has loaded the configuration files"
        failure == null
        env.getProperty('app.value', String).get() == 'a'
        env.getProperty('flaky.key', String).orElse(null) == (change == 'removed' ? null : 'new')
        !env.propertySources.any { it.is(flaky) }
        change == 'removed' || env.propertySources.find { it.name == 'flaky' }.is(replacement)

        and: "the next refresh does not read it either"
        env.refresh().getProperty('flaky.key', String).orElse(null) == (change == 'removed' ? null : 'new')

        cleanup:
        park.release.countDown()
        env.stop()

        where:
        [fails, change, read] << [['its order', 'its read'], ['removed', 'replaced'], ['start', 'refresh']].combinations()
    }

    void "a property source that replaces a registered one while a refresh runs a locator is only registered, is read by the refresh in place of the one it replaced, and fails the refresh when it fails to be read"() {
        given: "a registered property source that fails to be read now"
        Locator locator = new Locator()
        FailsWhenAsked flaky = new FailsWhenAsked(10)
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(PropertySource.of('other', ['other.key': 'other'], 5))
        env.addPropertySource(flaky)
        env.start()
        flaky.fail.set(true)
        PropertySource replacement = PropertySource.of('flaky', ['flaky.key': 'new', 'flaky.bad[99999999999]': 'x'], 10)
        Throwable failure = null

        when: "it is replaced, before the refresh takes the registered property sources, by one whose index cannot be read"
        Thread refresher = parkIn(locator) {
            try {
                env.refresh()
            } catch (Throwable t) {
                failure = t
            }
        }
        changeWhileParked {
            env.addPropertySource(replacement)
        }
        String duringRead = env.getProperty('flaky.key', String).orElse(null)
        release(locator, refresher)

        then: "the replacement was only registered"
        duringRead == 'flaky'

        and: "the refresh fails on the replacement, not on the one it replaced, and what was published stays"
        failure instanceof NumberFormatException
        env.getProperty('flaky.key', String).get() == 'flaky'
        env.getProperty('other.key', String).get() == 'other'
        !env.containsProperty('flaky.bad')
        env.propertySources*.name == ['other', 'flaky']
        env.propertySources.find { it.name == 'flaky' }.is(replacement)

        when:
        env.refresh()

        then: "the next refresh fails on it too"
        thrown(NumberFormatException)
        env.getProperty('flaky.key', String).get() == 'flaky'

        cleanup:
        locator.release.countDown()
        env.stop()
    }

    void "a property source removed while a refresh #phase is unregistered at once, and its properties stay until a refresh that has not taken it publishes"() {
        given:
        Gate gate = new Gate()
        Locator locator = new Locator()
        PropertySource extra = PropertySource.of('extra', ['extra.key': 'extra'])
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(extra)
        env.addPropertySource(gate)
        env.start()

        expect:
        env.getProperty('extra.key', String).get() == 'extra'

        when:
        Parking parking = phase == 'processes the property sources' ? gate : locator
        Thread refresher = parkIn(parking) { env.refresh() }
        changeWhileParked {
            env.removePropertySource(extra)
        }
        boolean registeredDuringRefresh = env.propertySources*.name.contains('extra')
        String duringRefresh = env.getProperty('extra.key', String).orElse(null)
        release(parking, refresher)

        then: "it is unregistered at once, and its properties stay while the refresh runs"
        !registeredDuringRefresh
        duringRefresh == 'extra'

        and: "the refresh publishes its properties only if it had taken it before it was removed"
        env.getProperty('extra.key', String).orElse(null) == afterRefresh
        !env.propertySources*.name.contains('extra')
        env.getProperty('app.value', String).get() == 'a'

        when:
        env.refresh()

        then: "the next refresh does not read it"
        !env.getProperty('extra.key', String).present
        !env.propertySources*.name.contains('extra')

        cleanup:
        gate.release.countDown()
        locator.release.countDown()
        env.stop()

        where:
        phase                            | afterRefresh
        'processes the property sources' | 'extra'
        'runs a locator'                 | null
    }

    /**
     * The configuration of an environment that reads the default property sources, and none of the
     * environment variables, and that, once armed, parks the read where it creates the environment
     * variables property source: after it has read the configuration files and before it takes the
     * registered property sources. With the given locator and configuration files, if any.
     */
    private static ApplicationContextConfiguration parkingInDefaults(Parking park, PropertySourcesLocator locator = null, Path dir = null) {
        new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() { ['test'] }

            @Override
            Optional<Boolean> getDeduceEnvironments() { Optional.of(false) }

            @Override
            List<String> getEnvironmentVariableIncludes() {
                park.pass()
                return ['MICRONAUT_ADD_BEFORE_INPUT_CUT_SPEC_NONE']
            }

            @Override
            List<String> getOverrideConfigLocations() { dir != null ? ['file:' + dir] : ['file:/nonexistent-add-before-input-cut-spec'] }

            @Override
            Collection<PropertySourcesLocator> getPropertySourcesLocators() { locator != null ? [locator] : [] }
        }
    }

    /**
     * The configuration of an environment that holds the property sources a test gives it and
     * nothing of the machine it runs on, with the given property sources locator.
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
                if (!release.await(AddBeforeInputCutSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the parked thread was never released')
                }
                return true
            }
            return false
        }
    }

    /**
     * A property sources locator that parks the read, once armed, and locates nothing.
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
     * A property source whose order, or whose iteration, fails once {@link #fail} is set.
     */
    static class FailsWhenSet implements PropertySource {
        final AtomicBoolean fail = new AtomicBoolean()
        private final String fails

        FailsWhenSet(String fails) {
            this.fails = fails
        }

        @Override
        String getName() { 'flaky' }

        @Override
        int getOrder() {
            if (fails == 'its order' && fail.get()) {
                throw new IllegalStateException('the order failed')
            }
            return 10
        }

        @Override
        Object get(String key) { key == 'flaky.key' ? 'flaky' : null }

        @Override
        Iterator<String> iterator() {
            if (fails == 'its read' && fail.get()) {
                throw new IllegalArgumentException('failed to read the property source')
            }
            return ['flaky.key'].iterator()
        }
    }

    /**
     * A property source that fails to be iterated once it is asked to.
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
}

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

/**
 * A property source added to or removed from a running environment while the environment reads
 * its property sources, when it starts or when it is refreshed. The read is parked, inside a
 * property source that it processes last or where it reads the default property sources, while
 * another thread changes the property sources, which must not wait for the read, and is then
 * released. Every wait is bounded.
 */
@Timeout(60)
class AddPropertySourceDuringReadSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "a property source added while a refresh reads the property sources is in the properties the refresh publishes"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)
        env.start()

        when: "another thread adds a property source while the refresh is parked in the middle of the read"
        Thread refresher = parkIn(gate) { env.refresh() }
        changeWhileParked {
            env.addPropertySource(PropertySource.of('late', ['late.key': 'late']))
        }

        then: "its properties are not published before the refresh publishes"
        !env.getProperty('late.key', String).present

        when:
        release(gate, refresher)

        then: "the refresh has published them with the others"
        env.getProperty('late.key', String).get() == 'late'
        env.getProperty('app.value', String).get() == 'a'

        and: "a later refresh keeps them"
        env.refresh().getProperty('late.key', String).get() == 'late'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "the difference of a refresh holds a property source added while it reads the property sources"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)
        env.start()
        Map<String, Object> diff = null

        when:
        Thread refresher = parkIn(gate) { diff = env.refreshAndDiff() }
        changeWhileParked {
            env.addPropertySource(PropertySource.of('late', ['late.key': 'late']))
        }
        release(gate, refresher)

        then: "the refresh has published it, so it reports it as changed"
        diff.containsKey('late.key')
        env.getProperty('late.key', String).get() == 'late'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source replaced while a refresh reads the property sources gives its new values once the refresh has published"() {
        given: "a property source that is replaced by one of the same name and order, as a configuration watch does"
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('remote', ['app.value': 'v1'], 10))
        env.addPropertySource(gate)
        env.start()

        when: "it is replaced while the refresh has read the previous one"
        Thread refresher = parkIn(gate) { env.refresh() }
        changeWhileParked {
            env.addPropertySource(PropertySource.of('remote', ['app.value': 'v2'], 10))
        }
        release(gate, refresher)

        then:
        env.getProperty('app.value', String).get() == 'v2'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "property sources added again in their order while a refresh reads, as the Consul configuration watch does, keep their precedence"() {
        given: "a remote property source, and one of a higher order that overrides one of its properties"
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('remote', ['app.value': 'remote-v1', 'app.remote': 'v1'], 10))
        env.addPropertySource(PropertySource.of('override', ['app.value': 'override'], 20))
        env.addPropertySource(gate)
        env.start()

        expect:
        env.getProperty('app.value', String).get() == 'override'

        when: "the remote one is replaced, and every registered property source is added again by order, while the refresh is parked"
        Thread refresher = parkIn(gate) { env.refresh() }
        changeWhileParked {
            List<PropertySource> updated = env.propertySources.collect {
                it.name == 'remote' ? PropertySource.of('remote', ['app.value': 'remote-v2', 'app.remote': 'v2'], 10) : it
            }
            updated.sort { it.order }.each { env.addPropertySource(it) }
        }
        release(gate, refresher)

        then: "the refresh publishes the new values, and the property source of the higher order still wins"
        env.getProperty('app.remote', String).get() == 'v2'
        env.getProperty('app.value', String).get() == 'override'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source added while a refresh reads, before the read has taken the registered property sources, is processed once in its order and stays registered"() {
        given: "a configuration that, once armed, parks the read where it reads the default property sources, before it takes the registered ones"
        Park park = new Park()
        DefaultEnvironment env = new DefaultEnvironment(new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() { ['test'] }

            @Override
            Optional<Boolean> getDeduceEnvironments() { Optional.of(false) }

            @Override
            List<String> getEnvironmentVariableIncludes() {
                park.pass()
                // no environment variable is read
                return ['MICRONAUT_ADD_PROPERTY_SOURCE_DURING_READ_SPEC_NONE']
            }
        })
        env.addPropertySource(PropertySource.of('higher', ['order.value': 'higher'], 100))
        env.start()

        when: "another thread adds a property source of a lower order that sets the same key, while the refresh is parked"
        park.arm()
        Thread refresher = daemon('refresher') { env.refresh() }
        assert park.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        changeWhileParked {
            env.addPropertySource(PropertySource.of('lower', ['order.value': 'lower', 'lower.key': 'lower'], -100))
        }
        park.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then: "the refresh processes it once, in its order, so the property source of the higher order keeps the key"
        !refresher.alive
        env.getProperty('lower.key', String).get() == 'lower'
        env.getProperty('order.value', String).get() == 'higher'

        and: "it stays registered, as a property source that is added does, rather than being dropped by the next refresh"
        env.refresh().getProperty('lower.key', String).get() == 'lower'
        env.propertySources*.name.contains('lower')

        cleanup:
        park.release.countDown()
        env.stop()
    }

    void "a property source added while the environment starts is in the properties once it has started"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)

        when: "another thread adds a property source while the start is parked in the middle of the read"
        Thread starter = parkIn(gate) { env.start() }
        changeWhileParked {
            env.addPropertySource(PropertySource.of('late', ['late.key': 'late']))
        }
        release(gate, starter)

        then:
        env.getProperty('late.key', String).get() == 'late'
        env.getProperty('app.value', String).get() == 'a'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source that a property sources locator adds while the environment reads is in the properties the read publishes"() {
        given: "a locator that adds a property source to the environment rather than returning it"
        PropertySourcesLocator locator = new PropertySourcesLocator() {
            @Override
            Collection<PropertySource> load(Environment environment) {
                environment.addPropertySource(PropertySource.of('located', ['located.key': 'located']))
                return []
            }
        }
        DefaultEnvironment env = new DefaultEnvironment(new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() { ['test'] }

            @Override
            boolean isEnableDefaultPropertySources() { false }

            @Override
            Collection<PropertySourcesLocator> getPropertySourcesLocators() { [locator] }
        })

        when:
        env.start()

        then: "it is not left for the next refresh to read"
        env.getProperty('located.key', String).get() == 'located'

        cleanup:
        env.stop()
    }

    void "a property source added, and another removed, while a refresh that fails runs: the added one is in the properties that stay published right after it, #failure"() {
        given: "a property source that, once armed, parks the read and then fails it"
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

        when: "a property source is added and another removed while a refresh reads, and the refresh then fails"
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
        release(gate, refresher)

        then: "the refresh fails, and the properties it read are not published"
        refreshFailure.is(failure)
        env.getProperty('app.value', String).get() == 'a'

        and: "the property source added meanwhile is registered, and its properties are published, as outside a refresh"
        env.propertySources*.name.contains('late')
        env.getProperty('late.key', String).get() == 'late'

        and: "the property source removed meanwhile stays removed"
        !env.propertySources*.name.contains('extra')

        when: "the next refresh succeeds"
        fail.set(false)
        env.refresh()

        then: "it reads the added property source with the others, and not the removed one"
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

    void "a property source replaced while a refresh that fails runs gives its new values right after it"() {
        given:
        AtomicBoolean fail = new AtomicBoolean()
        Gate gate = new Gate({
            if (fail.get()) {
                throw new IllegalStateException('failed to read the property source')
            }
        })
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('remote', ['app.value': 'v1'], 10))
        env.addPropertySource(gate)
        env.start()

        when: "it is replaced while a refresh reads, and the refresh then fails"
        fail.set(true)
        Thread refresher = parkIn(gate) {
            try {
                env.refresh()
            } catch (IllegalStateException ignored) {
                // expected
            }
        }
        changeWhileParked {
            env.addPropertySource(PropertySource.of('remote', ['app.value': 'v2'], 10))
        }
        release(gate, refresher)

        then:
        env.getProperty('app.value', String).get() == 'v2'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source removed and added again while a refresh reads the property sources gives its new values once the refresh has published"() {
        given:
        Gate gate = new Gate()
        PropertySource first = PropertySource.of('first', ['app.value': 'old'])
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(first)
        env.addPropertySource(gate)
        env.start()

        when: "another thread removes it and adds one of the same name while the refresh is parked"
        Thread refresher = parkIn(gate) { env.refresh() }
        changeWhileParked {
            env.removePropertySource(first)
            env.addPropertySource(PropertySource.of('first', ['app.value': 'new']))
        }
        release(gate, refresher)

        then:
        env.getProperty('app.value', String).get() == 'new'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source added and removed again while a refresh reads the property sources is not processed"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)
        env.start()
        PropertySource late = PropertySource.of('late', ['late.key': 'late'])

        when: "another thread adds a property source and removes it again while the refresh is parked"
        Thread refresher = parkIn(gate) { env.refresh() }
        changeWhileParked {
            env.addPropertySource(late)
            env.removePropertySource(late)
        }
        release(gate, refresher)

        then: "the refresh publishes nothing of it"
        !env.getProperty('late.key', String).present
        env.getProperty('app.value', String).get() == 'a'

        cleanup:
        gate.release.countDown()
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
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        int refreshes = 0
        while (refreshes < 50 && System.nanoTime() < deadline) {
            env.refresh()
            refreshes++
        }
        stop.set(true)
        adder.join(WAIT_SECONDS * 1000L)

        then: "every refresh finishes, and the last property source added gives the value"
        refreshes == 50
        !adder.alive
        env.getProperty('hot.value', Integer).get() == last.get()

        cleanup:
        stop.set(true)
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
     * Runs the read on another thread, once the gate is armed, and returns that thread once the
     * read is parked in the gate.
     */
    private static Thread parkIn(Gate gate, Closure<?> read) {
        gate.arm()
        Thread reader = daemon('reader', read)
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
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

    private static void release(Gate gate, Thread reader) {
        gate.release.countDown()
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
    static class Park {
        final CountDownLatch reached = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final AtomicBoolean armed = new AtomicBoolean()

        void arm() {
            armed.set(true)
        }

        void pass() {
            if (armed.compareAndSet(true, false)) {
                reached.countDown()
                if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the park was never released')
                }
            }
        }
    }

    /**
     * A property source without properties, processed after the others, that once armed parks the
     * thread that iterates it and then runs the given action.
     */
    static class Gate implements PropertySource {
        final CountDownLatch entered = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final AtomicBoolean armed = new AtomicBoolean()
        private final Runnable afterRelease

        Gate(Runnable afterRelease = {}) {
            this.afterRelease = afterRelease
        }

        void arm() {
            armed.set(true)
        }

        @Override
        String getName() { 'gate' }

        @Override
        int getOrder() { Ordered.LOWEST_PRECEDENCE }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                entered.countDown()
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                if (!release.await(AddPropertySourceDuringReadSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the gate was never released')
                }
                afterRelease.run()
            }
            return Collections.emptyIterator()
        }
    }
}

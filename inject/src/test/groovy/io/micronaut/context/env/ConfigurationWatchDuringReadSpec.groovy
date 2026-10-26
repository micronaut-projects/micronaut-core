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
 * A configuration watch, as micronaut-discovery-client's Consul watch is, that replaces a changed
 * property source by a new instance of its name and adds every registered property source again by
 * order to keep their precedence, while a refresh reads the property sources. Each add is published
 * at once, and the read publishes what it has read and the adds handed over to it in their order, so
 * a property source of a higher order keeps winning a shared key. The refresh is parked once it has
 * taken the registered property sources, and every wait is bounded.
 */
@Timeout(60)
class ConfigurationWatchDuringReadSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "property sources added again in their order while a refresh processes them, as the Consul configuration watch does, give the new values at once, and a property source of a higher order still wins a shared key once the refresh publishes"() {
        given: "a remote property source, and one of a higher order that overrides one of its properties"
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('remote', ['app.value': 'remote-v1', 'app.remote': 'v1'], 10))
        env.addPropertySource(PropertySource.of('override', ['app.value': 'override'], 20))
        env.addPropertySource(gate)
        env.start()

        expect:
        env.getProperty('app.value', String).get() == 'override'

        when: "the remote one is replaced by a new instance, and every registered property source is added again by order, while the refresh is parked"
        Thread refresher = parkIn(gate) { env.refresh() }
        changeWhileParked {
            List<PropertySource> updated = env.propertySources.collect {
                it.name == 'remote' ? PropertySource.of('remote', ['app.value': 'remote-v2', 'app.remote': 'v2'], 10) : it
            }
            updated.sort { it.order }.each { env.addPropertySource(it) }
        }
        String remoteDuringRefresh = env.getProperty('app.remote', String).orElse(null)
        String valueDuringRefresh = env.getProperty('app.value', String).orElse(null)
        release(gate, refresher)

        then: "the new values are visible at once, and the property source of the higher order, processed again on top, still wins"
        remoteDuringRefresh == 'v2'
        valueDuringRefresh == 'override'

        and: "the refresh publishes the new values with the same precedence, and the replacement in the place of the one it replaced"
        env.getProperty('app.remote', String).get() == 'v2'
        env.getProperty('app.value', String).get() == 'override'
        env.propertySources*.name == ['remote', 'override', 'gate']

        when:
        env.refresh()

        then:
        env.getProperty('app.remote', String).get() == 'v2'
        env.getProperty('app.value', String).get() == 'override'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source replaced while a refresh processes the property sources gives its new values at once, and keeps them once the refresh has published"() {
        given: "a property source that is replaced by one of the same name and order, as a configuration watch does"
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('remote', ['app.value': 'v1'], 10))
        env.addPropertySource(gate)
        env.start()
        PropertySource replacement = PropertySource.of('remote', ['app.value': 'v2'], 10)

        when: "it is replaced while the refresh has processed the previous one"
        Thread refresher = parkIn(gate) { env.refresh() }
        changeWhileParked {
            env.addPropertySource(replacement)
        }
        String duringRefresh = env.getProperty('app.value', String).orElse(null)
        release(gate, refresher)

        then: "the new values are visible at once, and the refresh publishes them after what it has read of the same order"
        duringRefresh == 'v2'
        env.getProperty('app.value', String).get() == 'v2'

        and: "the replacement is registered in the place of the property source it replaced"
        env.propertySources*.name == ['remote', 'gate']
        env.propertySources.find { it.name == 'remote' }.is(replacement)

        when:
        env.refresh()

        then:
        env.getProperty('app.value', String).get() == 'v2'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "property sources a bootstrap locator returned, one replaced and all added again in their order while a refresh runs that #outcome, as the Consul configuration watch does, keep their precedence, and the next refresh registers what the locator returns by that name again"() {
        given: "a bootstrap locator that returns what the remote configuration holds, as new property sources each time"
        AtomicBoolean fail = new AtomicBoolean()
        Gate gate = new Gate({
            if (fail.get()) {
                throw new IllegalStateException('failed to read the property source')
            }
        })
        AtomicReference<String> remote = new AtomicReference<>('1')
        ConsulLikeLocator locator = new ConsulLikeLocator({ int load ->
            [PropertySource.of('consul-a', ['k': 'A'], 10),
             PropertySource.of('consul-x', ['k': 'X' + remote.get(), 'x.only': remote.get()], 20),
             PropertySource.of('consul-b', ['k': 'B'], 30)]
        })
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(gate)
        env.start()
        PropertySource replacement = PropertySource.of('consul-x', ['k': 'X2', 'x.only': '2'], 20)
        Throwable failure = null

        expect:
        env.getProperty('k', String).get() == 'B'
        env.getProperty('x.only', String).get() == '1'

        when: "the remote configuration changes once the refresh has located it, and the watch replaces the changed one and adds every registered one again by order while the refresh is parked"
        fail.set(outcome == 'fails')
        Thread refresher = parkIn(gate) {
            try {
                env.refresh()
            } catch (Throwable t) {
                failure = t
            }
        }
        remote.set('2')
        changeWhileParked {
            env.propertySources
                .collect { it.name == 'consul-x' ? replacement : it }
                .sort { it.order }
                .each { env.addPropertySource(it) }
        }
        String kDuringRead = env.getProperty('k', String).orElse(null)
        String xDuringRead = env.getProperty('x.only', String).orElse(null)
        release(gate, refresher)

        then: "the new value was visible at once, and the property source of the highest order still wins the shared key"
        kDuringRead == 'B'
        xDuringRead == '2'
        outcome == 'succeeds' ? failure == null : failure instanceof IllegalStateException

        and: "so it is once the refresh has ended, and the replacement is registered"
        env.getProperty('k', String).get() == 'B'
        env.getProperty('x.only', String).get() == '2'
        env.propertySources.find { it.name == 'consul-x' }.is(replacement)
        env.propertySources*.name.sort() == ['consul-a', 'consul-b', 'consul-x', 'gate']

        when: "the environment is refreshed again"
        fail.set(false)
        env.refresh()

        then: "it takes the name of what it locates itself back, as 5.2.x did, and registers what the locator returns"
        env.getProperty('k', String).get() == 'B'
        env.getProperty('x.only', String).get() == '2'
        !env.propertySources.find { it.name == 'consul-x' }.is(replacement)
        env.propertySources*.name.sort() == ['consul-a', 'consul-b', 'consul-x', 'gate']

        when: "the remote configuration changes and the environment is refreshed"
        remote.set('3')
        env.refresh()

        then: "what the locator returns now is read"
        env.getProperty('x.only', String).get() == '3'
        env.getProperty('k', String).get() == 'B'

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        outcome << ['succeeds', 'fails']
    }

    void "a property source a bootstrap locator returned two reads before, added again while a refresh reads, stays registered until the next refresh, which registers what the locator returns by that name again"() {
        given:
        ConsulLikeLocator locator = new ConsulLikeLocator({ int load -> [PropertySource.of('consul-app', ['app.value': 'v' + load, ('only.v' + load): 'x'], 100)] })
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(gate)
        env.start()
        PropertySource started = env.propertySources.find { it.name == 'consul-app' }
        env.refresh()

        when: "a watch adds again, while the next refresh reads, the copy it took at start"
        Thread refresher = parkIn(gate) { env.refresh() }
        changeWhileParked {
            env.addPropertySource(started)
        }
        String duringRefresh = env.getProperty('app.value', String).orElse(null)
        release(gate, refresher)

        then: "the copy was visible at once"
        duringRefresh == 'v1'

        and: "the refresh publishes what it located, also the properties the old copy has not, with the copy after it, of the same order"
        env.getProperty('only.v3', String).present
        env.getProperty('app.value', String).get() == 'v1'
        env.getProperty('only.v1', String).present
        !env.getProperty('only.v2', String).present

        and: "the copy stays registered by that name, as an add during the read wins over what the read located"
        env.propertySources.find { it.name == 'consul-app' }.is(started)

        when: "the environment is refreshed again"
        env.refresh()

        then: "it takes the name back, as 5.2.x did: what the locator returns now is read and registered, and the copy is not read"
        env.getProperty('app.value', String).get() == 'v4'
        env.getProperty('only.v4', String).present
        !env.getProperty('only.v1', String).present
        !env.getProperty('only.v3', String).present
        !env.propertySources.find { it.name == 'consul-app' }.is(started)

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
                if (!release.await(ConfigurationWatchDuringReadSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
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
}

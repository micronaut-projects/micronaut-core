package io.micronaut.context.env

import io.micronaut.context.ApplicationContextConfiguration
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Which property sources a read of the property sources marks as refreshable, so that the next
 * refresh drops them and loads them again: only those a read loads or locates itself. A read is
 * parked while it loads the default property sources, when it asks the configuration which
 * environment variables to include.
 */
@Issue('https://github.com/micronaut-projects/micronaut-core/issues/14138')
@Timeout(60)
class RefreshableSourcesSpec extends Specification {

    static final int WAIT_SECONDS = 10

    void "a property source added while a #read loads the default property sources stays registered and published after the next refresh"() {
        given:
        ParkingConfiguration configuration = new ParkingConfiguration()
        DefaultEnvironment env = new DefaultEnvironment(configuration)
        if (read == 'refresh') {
            env.start()
        }

        when: "another thread adds a property source while the read loads the default property sources"
        configuration.arm()
        Thread reader = daemon(read) { read == 'refresh' ? env.refresh() : env.start() }
        assert configuration.parked.await(WAIT_SECONDS, TimeUnit.SECONDS)
        env.addPropertySource(PropertySource.of('added', ['added.key': 'added']))
        configuration.release.countDown()
        reader.join(WAIT_SECONDS * 1000L)

        then: "the read publishes it"
        !reader.alive
        env.getProperty('added.key', String).orElse(null) == 'added'

        when:
        env.refresh()

        then: "the next refresh keeps it registered and reads it"
        env.getProperty('added.key', String).orElse(null) == 'added'
        env.propertySources*.name.contains('added')

        cleanup:
        configuration.release.countDown()
        env.stop()

        where:
        read << ['refresh', 'start']
    }

    void "a property source a bootstrap locator returned to an earlier read, added again while a refresh loads the default property sources, is dropped by the next refresh once the locator returns nothing for it"() {
        given: "a bootstrap locator, as a distributed configuration client is, that returns its property source only on its first load"
        ParkingConfiguration configuration = new ParkingConfiguration()
        AtomicInteger loads = new AtomicInteger()
        configuration.locators = [new BootstrapLike({ loads.incrementAndGet() == 1 ? [PropertySource.of('located', ['located.key': 'v1'], 100)] : [] })]
        DefaultEnvironment env = new DefaultEnvironment(configuration)
        env.start()
        PropertySource located = env.propertySources.find { it.name == 'located' }

        expect:
        located != null
        env.getProperty('located.key', String).get() == 'v1'

        when: "a configuration watch adds the earlier instance again while a refresh, to which the locator returns nothing, loads the default property sources"
        configuration.arm()
        Thread refresher = daemon('refresh') { env.refresh() }
        assert configuration.parked.await(WAIT_SECONDS, TimeUnit.SECONDS)
        env.addPropertySource(located)
        configuration.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        and: "the environment is refreshed again"
        env.refresh()

        then: "the next refresh drops it, as it is one a read located itself"
        !refresher.alive
        !env.propertySources*.name.contains('located')
        !env.containsProperty('located.key')

        cleanup:
        configuration.release.countDown()
        env.stop()
    }

    void "a property source added while a refresh loads the default property sources, and returned by a #kind locator in that refresh, is dropped by a later refresh once the locator no longer returns it"() {
        given: "a locator that returns a property source on the second load only"
        ParkingConfiguration configuration = new ParkingConfiguration()
        AtomicInteger loads = new AtomicInteger()
        PropertySource shared = PropertySource.of('shared', ['shared.key': 'v1'], 100)
        Closure<List<PropertySource>> sources = { loads.incrementAndGet() == 2 ? [shared] : [] }
        configuration.locators = [kind == 'bootstrap' ? new BootstrapLike(sources) : new PlainLocator(sources)]
        DefaultEnvironment env = new DefaultEnvironment(configuration)
        env.start()

        when: "another thread adds the same instance while the refresh, whose locator returns it, loads the default property sources"
        configuration.arm()
        Thread refresher = daemon('refresh') { env.refresh() }
        assert configuration.parked.await(WAIT_SECONDS, TimeUnit.SECONDS)
        env.addPropertySource(shared)
        configuration.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then:
        !refresher.alive
        env.getProperty('shared.key', String).orElse(null) == 'v1'

        when: "the environment is refreshed twice more, and the locator returns nothing"
        env.refresh()
        env.refresh()

        then: "as a property source a read located itself, it is dropped"
        !env.propertySources*.name.contains('shared')
        !env.containsProperty('shared.key')

        cleanup:
        configuration.release.countDown()
        env.stop()

        where:
        kind << ['plain', 'bootstrap']
    }

    void "a property source added while a refresh loads the default property sources, by the name of one that refresh loads itself, is dropped by the next refresh"() {
        given:
        ParkingConfiguration configuration = new ParkingConfiguration()
        DefaultEnvironment env = new DefaultEnvironment(configuration)
        env.start()

        when: "another thread adds a property source by the name of the system properties property source while the refresh loads it"
        configuration.arm()
        Thread refresher = daemon('refresh') { env.refresh() }
        assert configuration.parked.await(WAIT_SECONDS, TimeUnit.SECONDS)
        env.addPropertySource(PropertySource.of(SystemPropertiesPropertySource.NAME, ['mine.value': 'mine']))
        configuration.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        and:
        env.refresh()

        then: "the next refresh drops that name and loads the system properties property source again"
        !refresher.alive
        env.propertySources.find { it.name == SystemPropertiesPropertySource.NAME } instanceof SystemPropertiesPropertySource
        !env.containsProperty('mine.value')

        cleanup:
        configuration.release.countDown()
        env.stop()
    }

    void "a property source registered before the start by the name of a default property source keeps it through two refreshes"() {
        given:
        ParkingConfiguration configuration = new ParkingConfiguration()
        DefaultEnvironment env = new DefaultEnvironment(configuration)
        PropertySource mine = PropertySource.of(SystemPropertiesPropertySource.NAME, ['mine.value': 'mine'])
        env.addPropertySource(mine)
        env.start()

        when:
        env.refresh()
        env.refresh()

        then:
        env.propertySources.find { it.name == SystemPropertiesPropertySource.NAME }.is(mine)
        env.getProperty('mine.value', String).orElse(null) == 'mine'

        cleanup:
        env.stop()
    }

    void "a property source added while a refresh loads the default property sources, and removed while the refresh registers the property sources it read, is not registered again"() {
        given: "a registered property source whose name, once armed, parks the refresh where it registers the property sources it read"
        ParkingConfiguration configuration = new ParkingConfiguration()
        ParksOnName parksOnName = new ParksOnName()
        DefaultEnvironment env = new DefaultEnvironment(configuration)
        env.addPropertySource(parksOnName)
        env.start()
        PropertySource added = PropertySource.of('added', ['added.key': 'added'])

        when: "another thread adds a property source while the refresh loads the default property sources"
        configuration.arm()
        Thread refresher = daemon('refresh') { env.refresh() }
        assert configuration.parked.await(WAIT_SECONDS, TimeUnit.SECONDS)
        env.addPropertySource(added)

        and: "removes it while the refresh registers the property sources it read"
        parksOnName.arm()
        configuration.release.countDown()
        assert parksOnName.parked.await(WAIT_SECONDS, TimeUnit.SECONDS)
        env.removePropertySource(added)
        parksOnName.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        and:
        env.refresh()

        then: "it stays removed"
        !refresher.alive
        !env.propertySources*.name.contains('added')
        !env.containsProperty('added.key')

        cleanup:
        configuration.release.countDown()
        parksOnName.release.countDown()
        env.stop()
    }

    private static Thread daemon(String name, Closure<?> work) {
        Thread thread = new Thread(work, name)
        thread.daemon = true
        thread.start()
        return thread
    }

    /**
     * A configuration with the default property sources that, once armed, parks the read that asks
     * it which environment variables to include, which the read does while it loads the default
     * property sources.
     */
    static class ParkingConfiguration implements ApplicationContextConfiguration {
        final CountDownLatch parked = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final AtomicBoolean armed = new AtomicBoolean()
        List<PropertySourcesLocator> locators = []

        void arm() {
            armed.set(true)
        }

        @Override
        List<String> getEnvironments() { ['test'] }

        @Override
        Optional<Boolean> getDeduceEnvironments() { Optional.of(false) }

        @Override
        Collection<PropertySourcesLocator> getPropertySourcesLocators() { locators }

        @Override
        List<String> getEnvironmentVariableIncludes() {
            if (armed.compareAndSet(true, false)) {
                parked.countDown()
                release.await(WAIT_SECONDS, TimeUnit.SECONDS)
            }
            return ['NO_SUCH_VARIABLE']
        }
    }

    /**
     * A property source without properties whose name, once armed, parks the thread that asks for
     * it, which a read does when it registers the property sources it read.
     */
    static class ParksOnName implements PropertySource {
        final CountDownLatch parked = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final AtomicBoolean armed = new AtomicBoolean()

        void arm() {
            armed.set(true)
        }

        @Override
        String getName() {
            if (armed.compareAndSet(true, false)) {
                parked.countDown()
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                release.await(RefreshableSourcesSpec.WAIT_SECONDS, TimeUnit.SECONDS)
            }
            return 'parks-on-name'
        }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() { Collections.emptyIterator() }
    }

    /**
     * A locator whose property sources are processed but not registered.
     */
    static class PlainLocator implements PropertySourcesLocator {
        private final Closure<List<PropertySource>> sources

        PlainLocator(Closure<List<PropertySource>> sources) {
            this.sources = sources
        }

        @Override
        Collection<PropertySource> load(Environment environment) {
            return sources.call()
        }
    }

    /**
     * A bootstrap locator: what it returns is registered like the configuration files.
     */
    static class BootstrapLike implements PropertySourcesLocator, BootstrapLocatorMarker {
        private final Closure<List<PropertySource>> sources

        BootstrapLike(Closure<List<PropertySource>> sources) {
            this.sources = sources
        }

        @Override
        Collection<PropertySource> load(Environment environment) {
            return sources.call()
        }
    }
}

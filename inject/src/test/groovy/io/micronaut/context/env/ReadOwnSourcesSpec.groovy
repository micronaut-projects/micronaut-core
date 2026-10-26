package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.core.io.ResourceLoader
import io.micronaut.core.order.Ordered
import spock.lang.Specification
import spock.lang.Timeout

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * The property sources a read reads itself: the configuration files, the default property sources
 * and what a bootstrap locator returns. The thread that reads sees them in place of those the read
 * before read itself, other threads see the registered ones until the read ends, and the read
 * registers its own unless its name was added or removed during the read, or is held by a property
 * source it has not read itself. The threads are parked at fixed points, and every wait is bounded.
 */
@Timeout(60)
class ReadOwnSourcesSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "a system property keeps its value, and the system properties and environment variables property sources stay registered, however often the environment is refreshed"() {
        given:
        System.setProperty('read.own.sources.spec.foo', '1')
        DefaultEnvironment env = new DefaultEnvironment(ApplicationContext.builder()
                .environments('test')
                .deduceEnvironment(false) as ApplicationContextConfiguration)
        List<String> defaults = [SystemPropertiesPropertySource.NAME, EnvironmentPropertySource.NAME]
        env.start()

        when: "the environment is refreshed twice"
        List<List<Object>> afterRefresh = (1..2).collect {
            env.refresh()
            [env.getProperty('read.own.sources.spec.foo', String).orElse(null), env.propertySources*.name.findAll { it in defaults }.sort()]
        }

        then:
        afterRefresh == [['1', ['env', 'system']], ['1', ['env', 'system']]]
        env.propertySources.find { it.name == SystemPropertiesPropertySource.NAME } instanceof SystemPropertiesPropertySource
        env.propertySources.find { it.name == EnvironmentPropertySource.NAME } instanceof EnvironmentPropertySource

        cleanup:
        System.clearProperty('read.own.sources.spec.foo')
        env.stop()
    }

    void "a property sources locator sees the configuration file and the default property sources of the read that runs it, another thread sees those of the read before until the read ends, and a property source registered by its own name stays registered"() {
        given: "a configuration file, the default property sources, a locator that looks at the property sources, and a property source of the user's"
        Path dir = Files.createTempDirectory('read-own-sources-view-')
        Files.writeString(dir.resolve('application.properties'), 'file.value=file\n')
        ViewingLocator locator = new ViewingLocator()
        DefaultEnvironment env = new DefaultEnvironment(readingDefaults(null, dir, locator))
        PropertySource user = PropertySource.of('user', ['user.value': 'user'])
        env.addPropertySource(user)
        List<String> own = ['application', SystemPropertiesPropertySource.NAME, EnvironmentPropertySource.NAME]

        when: "the environment starts and is refreshed twice"
        env.start()
        List<Map<String, PropertySource>> registered = [byName(env.propertySources)]
        for (int i = 0; i < 2; i++) {
            env.refresh()
            registered << byName(env.propertySources)
        }

        then: "the locator ran once for each read"
        locator.seen.size() == 3
        locator.others.size() == 3

        and: "each read saw the property sources it read itself, which it registered"
        (0..2).every { int i -> own.every { String name -> locator.seen[i][name] != null && locator.seen[i][name].is(registered[i][name]) } }

        and: "a refresh did not see those that the read before read itself, which another thread still saw while the refresh ran"
        (1..2).every { int i -> own.every { String name -> !locator.seen[i].values().any { it.is(registered[i - 1][name]) } } }
        (1..2).every { int i -> own.every { String name -> locator.others[i][name] != null && locator.others[i][name].is(registered[i - 1][name]) } }

        and: "every read saw the property source of the user's, and it stays registered"
        locator.seen.every { it['user'].is(user) }
        registered.every { it['user'].is(user) }
        env.getProperty('user.value', String).get() == 'user'
        env.getProperty('file.value', String).get() == 'file'

        cleanup:
        env.stop()
        dir.toFile().deleteDir()
    }

    void "a property source added during a refresh by the name of a default property source that the refresh reads itself is registered in its place, and the next refresh keeps it: added #phase"() {
        given: "the default property sources, and a gate that parks the refresh after it has processed them, once armed"
        Parking defaults = new Parking()
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(readingDefaults(defaults))
        env.addPropertySource(gate)
        env.start()
        PropertySource mine = PropertySource.of(SystemPropertiesPropertySource.NAME, ['mine.value': 'mine'])

        expect:
        env.propertySources.find { it.name == SystemPropertiesPropertySource.NAME } instanceof SystemPropertiesPropertySource

        when: "another thread adds it while the refresh is parked"
        Parking parking = phase == 'before the refresh takes the registered property sources' ? defaults : gate
        Thread refresher = parkIn(parking) { env.refresh() }
        changeWhileParked {
            env.addPropertySource(mine)
        }
        release(parking, refresher)

        then: "the refresh has not registered the property source it read by that name in its place"
        env.propertySources.find { it.name == SystemPropertiesPropertySource.NAME }.is(mine)
        env.getProperty('mine.value', String).orElse(null) == 'mine'

        when: "the environment is refreshed again"
        env.refresh()

        then: "it is not dropped, and it is read"
        env.propertySources.find { it.name == SystemPropertiesPropertySource.NAME }.is(mine)
        env.getProperty('mine.value', String).orElse(null) == 'mine'

        cleanup:
        defaults.release.countDown()
        gate.release.countDown()
        env.stop()

        where:
        phase << ['before the refresh takes the registered property sources', 'while the refresh processes them']
    }

    void "a property source registered before the start by the name of one that #kind gives stays registered, and the start and every refresh read both"() {
        given: "a property source of the user's registered by the name of what the read reads itself"
        Path dir = Files.createTempDirectory('read-own-sources-')
        Path file = dir.resolve('application.properties')
        if (kind == 'a configuration file') {
            Files.writeString(file, 'own.key=v1\n')
        }
        AtomicReference<String> remote = new AtomicReference<>('v1')
        PropertySourcesLocator locator = new ConsulLikeLocator({ int load -> [PropertySource.of('application', ['own.key': remote.get()], 10)] })
        List<PropertySourcesLocator> locators = kind == 'a bootstrap locator' ? [locator] : []
        DefaultEnvironment env = new DefaultEnvironment(new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() { ['test'] }

            @Override
            Optional<Boolean> getDeduceEnvironments() { Optional.of(false) }

            @Override
            boolean isEnvironmentPropertySource() { false }

            @Override
            List<String> getOverrideConfigLocations() { ['file:' + dir] }

            @Override
            Collection<PropertySourcesLocator> getPropertySourcesLocators() { locators }
        })
        PropertySource registered = PropertySource.of('application', ['registered.key': 'registered'])
        env.addPropertySource(registered)

        when:
        env.start()

        then: "the start reads both, and the one of the user's stays registered"
        env.propertySources.find { it.name == 'application' }.is(registered)
        env.getProperty('registered.key', String).get() == 'registered'
        env.getProperty('own.key', String).get() == 'v1'

        when: "what the read reads itself changes, and the environment is refreshed twice"
        if (kind == 'a configuration file') {
            Files.writeString(file, 'own.key=v2\n')
        } else {
            remote.set('v2')
        }
        env.refresh()
        List<Object> afterFirst = [env.propertySources.find { it.name == 'application' }, env.getProperty('registered.key', String).orElse(null), env.getProperty('own.key', String).orElse(null)]
        env.refresh()

        then: "each refresh reads both again, and the one of the user's stays registered"
        afterFirst[0].is(registered)
        afterFirst[1] == 'registered'
        afterFirst[2] == 'v2'
        env.propertySources.find { it.name == 'application' }.is(registered)
        env.getProperty('registered.key', String).get() == 'registered'
        env.getProperty('own.key', String).get() == 'v2'

        cleanup:
        env.stop()
        dir.toFile().deleteDir()

        where:
        kind << ['a configuration file', 'a bootstrap locator']
    }

    void "a property source added while the environment reads, by the name of a configuration file's, is processed after the file's properties and registered in place of the file until the next refresh, which takes the name back: #read"() {
        given:
        Path dir = Files.createTempDirectory('read-own-sources-')
        Path file = dir.resolve('application.properties')
        Files.writeString(file, 'file.only=f\nk=f\n')
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() { ['test'] }

            @Override
            Optional<Boolean> getDeduceEnvironments() { Optional.of(false) }

            @Override
            boolean isEnvironmentPropertySource() { false }

            @Override
            List<String> getOverrideConfigLocations() { ['file:' + dir] }
        })
        env.addPropertySource(gate)
        if (read == 'refresh') {
            env.start()
        }
        PropertySource added = PropertySource.of('application', ['k': 'x', 'x.only': 'x'], 50)

        when: "it is added while the read is parked, after it has read the file"
        Thread reader = parkIn(gate) { read == 'refresh' ? env.refresh() : env.start() }
        changeWhileParked {
            env.addPropertySource(added)
        }
        String fileOnlyDuringRead = env.getProperty('file.only', String).orElse(null)
        String kDuringRead = env.getProperty('k', String).orElse(null)
        String xOnlyDuringRead = env.getProperty('x.only', String).orElse(null)
        release(gate, reader)

        then: "it was processed at once, on top of the properties published so far"
        fileOnlyDuringRead == 'f'
        kDuringRead == 'x'
        xOnlyDuringRead == 'x'

        and: "the read publishes it after the file it has read, of a lower order, and registers it rather than the file"
        env.getProperty('file.only', String).get() == 'f'
        env.getProperty('k', String).get() == 'x'
        env.getProperty('x.only', String).get() == 'x'
        env.propertySources.find { it.name == 'application' }.is(added)

        when:
        env.refresh()
        env.refresh()

        then: "the next refresh registers the file by its name again, as 5.2.x dropped such a name when it read again"
        env.getProperty('file.only', String).get() == 'f'
        env.getProperty('k', String).get() == 'f'
        !env.getProperty('x.only', String).present
        !env.propertySources.find { it.name == 'application' }.is(added)

        cleanup:
        gate.release.countDown()
        env.stop()
        dir.toFile().deleteDir()

        where:
        read << ['start', 'refresh']
    }

    void "a configuration file's property source that the previous read loaded, added again while a refresh reads, #phase, is not handed over: the refresh publishes and registers what it reads from the file now, which #file"() {
        given: "configuration files, a configuration that parks the refresh before it takes the registered property sources, and a gate that parks it after"
        Path dir = Files.createTempDirectory('read-own-sources-')
        Files.writeString(dir.resolve('application.properties'), 'base=1\n')
        Path testFile = dir.resolve('application-test.properties')
        Files.writeString(testFile, 't.value=v1\nt.removed=x\n')
        Parking park = new Parking()
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(readingDefaults(park, dir))
        env.addPropertySource(gate)
        env.start()
        PropertySource previous = env.propertySources.find { it.name == 'application-test' }

        expect:
        previous != null
        env.getProperty('t.value', String).get() == 'v1'

        when: "the file changes, and the property source the previous read loaded from it is added again while a refresh is parked"
        if (file == 'has changed') {
            Files.writeString(testFile, 't.value=v2\n')
        } else {
            Files.delete(testFile)
        }
        Parking parking = phase == 'before it takes the registered property sources' ? park : gate
        Thread refresher = parkIn(parking) { env.refresh() }
        changeWhileParked {
            env.addPropertySource(previous)
        }
        String valueDuringRefresh = env.getProperty('t.value', String).orElse(null)
        String removedDuringRefresh = env.getProperty('t.removed', String).orElse(null)
        release(parking, refresher)

        then: "adding it again changed no value"
        valueDuringRefresh == 'v1'
        removedDuringRefresh == 'x'

        and: "the refresh publishes what it has read from the file now, and registers the property source it has read in place of the previous one"
        env.getProperty('t.value', String).orElse(null) == valueAfter
        !env.getProperty('t.removed', String).present
        env.getProperty('base', String).get() == '1'
        !env.propertySources.any { it.is(previous) }
        file == 'is deleted' ? !env.propertySources*.name.contains('application-test') : env.propertySources.find { it.name == 'application-test' }.get('t.value') == 'v2'

        and: "the next refreshes read the file again"
        env.refresh().getProperty('t.value', String).orElse(null) == valueAfter
        env.refresh().getProperty('t.value', String).orElse(null) == valueAfter
        !env.getProperty('t.removed', String).present

        cleanup:
        park.release.countDown()
        gate.release.countDown()
        env.stop()
        dir.toFile().deleteDir()

        where:
        [phase, file] << [['before it takes the registered property sources', 'once it has taken them'], ['has changed', 'is deleted']].combinations()
        valueAfter = file == 'has changed' ? 'v2' : null
    }

    void "a property source a bootstrap locator returned to the previous read, added again while a refresh reads, #phase, is not handed over: the refresh publishes and registers what the locator #kind"() {
        given: "a bootstrap locator that parks the refresh, once armed, before it takes the registered property sources, and a gate that parks it after"
        Map<String, Object> sameValues = ['app.value': 'v1']
        MapPropertySource same = new MapPropertySource('consul-app', sameValues)
        BootstrapScriptedLocator locator = new BootstrapScriptedLocator({ int load ->
            if (kind == 'returns now, a new instance') {
                return [PropertySource.of('consul-app', ['app.value': 'v' + load], 100)]
            } else if (kind == 'returns now, the same instance whose values have changed') {
                return [same]
            }
            return load == 1 ? [PropertySource.of('consul-app', ['app.value': 'v1'], 100)] : []
        })
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withLocator(locator))
        env.addPropertySource(gate)
        env.start()
        PropertySource previous = env.propertySources.find { it.name == 'consul-app' }

        expect:
        env.getProperty('app.value', String).get() == 'v1'

        when: "the watch adds again, while the refresh is parked, the instance it took before that refresh"
        Parking parking = phase == 'before it takes the registered property sources' ? locator : gate
        Thread refresher = parkIn(parking) { env.refresh() }
        changeWhileParked {
            if (kind == 'returns now, the same instance whose values have changed') {
                sameValues.put('app.value', 'v2')
            }
            env.addPropertySource(previous)
        }
        String duringRefresh = env.getProperty('app.value', String).orElse(null)
        release(parking, refresher)

        then: "added before, it was only registered; added after, it was processed again at once"
        duringRefresh == during

        and: "the refresh publishes what it has read itself, and registers what the locator returned it"
        env.getProperty('app.value', String).orElse(null) == after
        kind == 'no longer returns' ? !env.propertySources*.name.contains('consul-app') : true
        kind == 'returns now, a new instance' ? !env.propertySources.find { it.name == 'consul-app' }.is(previous) : true
        kind == 'returns now, a new instance' ? env.propertySources.find { it.name == 'consul-app' }.get('app.value') == 'v2' : true
        kind == 'returns now, the same instance whose values have changed' ? env.propertySources.find { it.name == 'consul-app' }.is(previous) : true

        when:
        env.refresh()

        then: "the next refresh reads what the locator returns then"
        env.getProperty('app.value', String).orElse(null) == next

        cleanup:
        locator.release.countDown()
        gate.release.countDown()
        env.stop()

        where:
        phase                                             | kind                                                       | during | after | next
        'before it takes the registered property sources' | 'returns now, a new instance'                              | 'v1'   | 'v2'  | 'v3'
        'once it has taken them'                          | 'returns now, a new instance'                              | 'v1'   | 'v2'  | 'v3'
        'before it takes the registered property sources' | 'returns now, the same instance whose values have changed' | 'v1'   | 'v2'  | 'v2'
        'once it has taken them'                          | 'returns now, the same instance whose values have changed' | 'v2'   | 'v1'  | 'v2'
        'before it takes the registered property sources' | 'no longer returns'                                        | 'v1'   | null  | null
        'once it has taken them'                          | 'no longer returns'                                        | 'v1'   | null  | null
    }

    void "a configuration file property source whose name waits for another thread to add a property source does not keep that add waiting, and both are published: #read"() {
        given: "a loader that gives a property source whose name, when first asked for, waits for another thread to add one"
        AddsWhenNamed named = new AddsWhenNamed()
        DefaultEnvironment env = new DefaultEnvironment(new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() { ['test'] }

            @Override
            Optional<Boolean> getDeduceEnvironments() { Optional.of(false) }

            @Override
            List<String> getEnvironmentVariableIncludes() { ['MICRONAUT_READ_OWN_SOURCES_SPEC_NONE'] }
        })
        named.env = env
        env.@propertySourceLoaderList = [new GivesSource(named)]
        if (read == 'refresh') {
            env.start()
        }

        when:
        named.armed.set(true)
        Thread reader = daemon('reader') { read == 'refresh' ? env.refresh() : env.start() }
        reader.join(WAIT_SECONDS * 1000L)

        then: "the other thread's add does not wait for the read, and both are published and registered"
        !reader.alive
        named.addedInTime
        env.getProperty('own.key', String).get() == 'own'
        env.getProperty('other.key', String).get() == 'other'
        env.propertySources*.name.containsAll(['own', 'other'])

        cleanup:
        named.armed.set(false)
        env.stop()

        where:
        read << ['start', 'refresh']
    }

    /**
     * The configuration of an environment that reads the default property sources, and none of the
     * environment variables, and that, once the given parking is armed, parks the read where it
     * creates the environment variables property source: after it has read the configuration files
     * and before it takes the registered property sources. With the configuration files of the
     * given directory, if any, and the given locator, if any.
     */
    private static ApplicationContextConfiguration readingDefaults(Parking park, Path dir = null, PropertySourcesLocator locator = null) {
        new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() { ['test'] }

            @Override
            Optional<Boolean> getDeduceEnvironments() { Optional.of(false) }

            @Override
            List<String> getEnvironmentVariableIncludes() {
                park?.pass()
                return ['READ_OWN_SOURCES_SPEC_NO_SUCH_VARIABLE']
            }

            @Override
            List<String> getOverrideConfigLocations() { dir != null ? ['file:' + dir] : ['file:/nonexistent-read-own-sources-spec'] }

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

    private static Map<String, PropertySource> byName(Collection<PropertySource> sources) {
        Map<String, PropertySource> named = [:]
        for (PropertySource source : sources) {
            named.put(source.name, source)
        }
        return named
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
                if (!release.await(ReadOwnSourcesSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
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
     * A bootstrap locator that, once armed, parks the read before it returns what the given closure
     * gives for the number of the load.
     */
    static class BootstrapScriptedLocator extends Parking implements PropertySourcesLocator, BootstrapLocatorMarker {
        private final Closure<List<PropertySource>> sources
        private final AtomicInteger loads = new AtomicInteger()

        BootstrapScriptedLocator(Closure<List<PropertySource>> sources) {
            this.sources = sources
        }

        @Override
        Collection<PropertySource> load(Environment environment) {
            pass()
            return sources.call(loads.incrementAndGet())
        }
    }

    /**
     * A property sources locator that records the property sources the read that runs it sees,
     * and those another thread sees meanwhile, and locates nothing.
     */
    static class ViewingLocator implements PropertySourcesLocator {
        final List<Map<String, PropertySource>> seen = new CopyOnWriteArrayList<>()
        final List<Map<String, PropertySource>> others = new CopyOnWriteArrayList<>()

        @Override
        Collection<PropertySource> load(Environment environment) {
            seen.add(named(environment.getPropertySources()))
            AtomicReference<Map<String, PropertySource>> other = new AtomicReference<>()
            Thread thread = new Thread({ other.set(named(environment.getPropertySources())) }, 'other-thread')
            thread.daemon = true
            thread.start()
            thread.join(ReadOwnSourcesSpec.WAIT_SECONDS * 1000L)
            others.add(other.get() ?: [:])
            return []
        }

        private static Map<String, PropertySource> named(Collection<PropertySource> sources) {
            Map<String, PropertySource> named = [:]
            for (PropertySource source : sources) {
                named.put(source.name, source)
            }
            return named
        }
    }

    /**
     * A property source whose name, the first time it is asked for once armed, has another thread add
     * a property source, and waits for it a while.
     */
    static class AddsWhenNamed implements PropertySource {
        volatile Environment env
        final AtomicBoolean armed = new AtomicBoolean()
        volatile boolean addedInTime

        @Override
        String getName() {
            if (armed.compareAndSet(true, false)) {
                Environment environment = env
                Thread adder = new Thread({ environment.addPropertySource(PropertySource.of('other', ['other.key': 'other'])) } as Runnable, 'adder')
                adder.daemon = true
                adder.start()
                adder.join(3000)
                addedInTime = !adder.alive
            }
            return 'own'
        }

        @Override
        Object get(String key) { key == 'own.key' ? 'own' : null }

        @Override
        Iterator<String> iterator() { ['own.key'].iterator() }
    }

    /**
     * A property source loader that gives the given property source for each configuration file.
     */
    static class GivesSource implements PropertySourceLoader {
        private final PropertySource source

        GivesSource(PropertySource source) {
            this.source = source
        }

        @Override
        Optional<PropertySource> load(String resourceName, ResourceLoader resourceLoader) { Optional.of(source) }

        @Override
        Optional<PropertySource> loadEnv(String resourceName, ResourceLoader resourceLoader, ActiveEnvironment activeEnvironment) { Optional.empty() }

        @Override
        Map<String, Object> read(String name, InputStream input) throws IOException { [:] }
    }
}

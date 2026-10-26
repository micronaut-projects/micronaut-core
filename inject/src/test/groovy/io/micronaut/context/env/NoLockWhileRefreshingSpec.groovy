package io.micronaut.context.env

import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.core.io.ResourceLoader
import spock.lang.Specification
import spock.lang.Timeout

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Function

/**
 * The code of the application that refreshing the environment and adding property sources runs:
 * property sources, property source loaders, property sources locators, converters, and the values
 * a difference compares. None of it runs with a lock of the environment held, so none of it can
 * deadlock with a thread that holds a lock it needs and waits for the environment.
 */
@Timeout(60)
class NoLockWhileRefreshingSpec extends Specification {

    void "property sources, a property source loader, a property sources locator, a converter and the values a refreshAndDiff compares run with no lock of the environment held"() {
        given: "a property source loader that only this environment loads, a locator, and a property source that converts a property and gives a value whose equals is watched"
        Path dir = Files.createTempDirectory('no-lock-while-refreshing-')
        URLClassLoader classLoader = probeLoaderClassLoader(dir)
        LockProbe probe = new LockProbe()
        DefaultEnvironment env = new DefaultEnvironment(probing(classLoader, dir, new ProbeLocator(probe)))
        probe.watch(env)
        ProbeLoader.probe = probe
        env.getConversionService().addConverter(String, Endpoint, { String text ->
            probe.check('converter')
            return new Endpoint(text)
        } as Function<String, Endpoint>)
        env.addPropertySource(PropertySource.of('endpoint', ['app.endpoint': 'http://localhost']))
        env.addPropertySource(new ProbeSource('probe', probe, env))

        when: "the environment starts, is refreshed, is refreshed with a difference, and a property source is added"
        env.start()
        Set<String> started = probe.take()
        env.refresh()
        Set<String> refreshed = probe.take()
        env.refreshAndDiff()
        Set<String> diffed = probe.take()
        env.addPropertySource(new ProbeSource('added', probe, env))
        Set<String> added = probe.take()

        then: "each of them ran"
        started.containsAll(['source', 'loader', 'locator', 'converter'])
        refreshed.containsAll(['source', 'loader', 'locator', 'converter'])
        diffed.containsAll(['source', 'loader', 'locator', 'converter', 'equals'])
        added.containsAll(['source', 'converter'])

        and: "none of them held the monitor of the environment, that of its property sources, or the lock that publishes the properties"
        probe.held.isEmpty()

        cleanup:
        ProbeLoader.probe = null
        env.stop()
        classLoader.close()
        dir.toFile().deleteDir()
    }

    /**
     * The configuration of an environment that reads the default property sources, none of the
     * environment variables, and the configuration files of the given directory, with the given
     * class loader and locator.
     */
    private static ApplicationContextConfiguration probing(ClassLoader loader, Path dir, PropertySourcesLocator locator) {
        new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() { ['test'] }

            @Override
            Optional<Boolean> getDeduceEnvironments() { Optional.of(false) }

            @Override
            ClassLoader getClassLoader() { loader }

            @Override
            List<String> getEnvironmentVariableIncludes() { ['NO_LOCK_WHILE_REFRESHING_SPEC_NO_SUCH_VARIABLE'] }

            @Override
            List<String> getOverrideConfigLocations() { ['file:' + dir] }

            @Override
            Collection<PropertySourcesLocator> getPropertySourcesLocators() { [locator] }
        }
    }

    /**
     * A class loader that makes {@link ProbeLoader} a property source loader of the environments
     * that use it, and of no other.
     */
    private static URLClassLoader probeLoaderClassLoader(Path dir) {
        Path services = dir.resolve('META-INF/services/' + PropertySourceLoader.name)
        Files.createDirectories(services.parent)
        Files.writeString(services, ProbeLoader.name)
        return new URLClassLoader([dir.toUri().toURL()] as URL[], NoLockWhileRefreshingSpec.classLoader)
    }

    /**
     * Records what has run, and whether it held the monitor of the environment, the monitor of its
     * property sources or the lock its resolver publishes the properties under.
     */
    static class LockProbe {
        final Set<String> ran = ConcurrentHashMap.newKeySet()
        final List<String> held = new CopyOnWriteArrayList<>()
        private volatile Object environment
        private volatile Object registered
        private volatile Object publishLock

        void watch(DefaultEnvironment env) {
            environment = env
            registered = env.@propertySources
            publishLock = ((PropertySourcePropertyResolver) env.delegate()).@publishLock
        }

        void check(String what) {
            ran.add(what)
            if (Thread.holdsLock(environment)) {
                held.add(what + ' held the monitor of the environment')
            }
            if (Thread.holdsLock(registered)) {
                held.add(what + ' held the monitor of the property sources')
            }
            if (Thread.holdsLock(publishLock)) {
                held.add(what + ' held the lock that publishes the properties')
            }
        }

        Set<String> take() {
            Set<String> taken = new HashSet<>(ran)
            ran.clear()
            return taken
        }
    }

    /**
     * A property source loader, found with the service loader, that checks the locks when it is
     * asked for a configuration file, and finds none.
     */
    static class ProbeLoader implements PropertySourceLoader {
        static volatile LockProbe probe

        @Override
        Set<String> getExtensions() {
            Set.of('probe')
        }

        @Override
        Optional<PropertySource> load(String resourceName, ResourceLoader resourceLoader) {
            LockProbe current = probe
            if (current != null) {
                current.check('loader')
            }
            return Optional.empty()
        }

        @Override
        Optional<PropertySource> loadEnv(String resourceName, ResourceLoader resourceLoader, ActiveEnvironment activeEnvironment) {
            Optional.empty()
        }

        @Override
        Map<String, Object> read(String name, InputStream input) {
            [:]
        }
    }

    /**
     * A property sources locator that checks the locks when it loads, and locates nothing.
     */
    static class ProbeLocator implements PropertySourcesLocator {
        private final LockProbe probe

        ProbeLocator(LockProbe probe) {
            this.probe = probe
        }

        @Override
        Collection<PropertySource> load(Environment environment) {
            probe.check('locator')
            return []
        }
    }

    /**
     * A property source that checks the locks when it is iterated and read, converts
     * {@code app.endpoint} when it is read, and gives a new {@link ProbedValue} every time.
     */
    static class ProbeSource implements PropertySource {
        private final String name
        private final LockProbe probe
        private final Environment env

        ProbeSource(String name, LockProbe probe, Environment env) {
            this.name = name
            this.probe = probe
            this.env = env
        }

        @Override
        String getName() { name }

        @Override
        int getOrder() { 10 }

        @Override
        Object get(String key) {
            probe.check('source')
            if (key == name + '.endpoint') {
                Optional<Endpoint> endpoint = env.getProperty('app.endpoint', Endpoint)
                return endpoint.isPresent() ? endpoint.get().text : null
            }
            if (key == name + '.value') {
                return new ProbedValue(probe, 'same')
            }
            return null
        }

        @Override
        Iterator<String> iterator() {
            probe.check('source')
            return [name + '.endpoint', name + '.value'].iterator()
        }
    }

    /**
     * A value that checks the locks when it is compared.
     */
    static class ProbedValue {
        private final LockProbe probe
        private final String text

        ProbedValue(LockProbe probe, String text) {
            this.probe = probe
            this.text = text
        }

        @Override
        boolean equals(Object other) {
            probe.check('equals')
            return other instanceof ProbedValue && ((ProbedValue) other).text == text
        }

        @Override
        int hashCode() { text.hashCode() }

        @Override
        String toString() { text }
    }

    /**
     * A type that a property is converted to by a converter of the test.
     */
    static class Endpoint {
        final String text

        Endpoint(String text) {
            this.text = text
        }
    }
}

package io.micronaut.context

import io.micronaut.context.annotation.BootstrapContextCompatible
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.ActiveEnvironment
import io.micronaut.context.env.BootstrapPropertySourceLocator
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.PropertySourceLoader
import io.micronaut.core.io.ResourceLoader
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class BootstrapContextRefreshSpec extends Specification {

    def setup() {
        CountingLocator.instances.clear()
        CountingLocator.failure = null
        CountingLocator.remoteValue = null
        OnceLoader.instances.set(0)
    }

    void "a refresh reuses the bootstrap context, and closing the context closes it"() {
        given:
        ApplicationContext ctx = startContext()

        expect: "the start reads the bootstrap configuration with one bootstrap context"
        CountingLocator.created() == 1
        CountingLocator.running() == 1

        when:
        5.times { ctx.environment.refresh() }

        then: "each refresh reads it again with the same bootstrap context, whose beans are not destroyed"
        CountingLocator.created() == 1
        CountingLocator.running() == 1
        CountingLocator.instances[0].reads == 6

        when:
        ctx.close()

        then:
        CountingLocator.running() == 0
    }

    void "many refreshes leave one bootstrap context running"() {
        given:
        ApplicationContext ctx = startContext()

        when:
        200.times { ctx.environment.refresh() }

        then: "5.0.0 to 5.2.15 left 201 bootstrap contexts running here"
        CountingLocator.created() == 1
        CountingLocator.running() == 1
        CountingLocator.instances[0].reads == 201

        when:
        ctx.close()

        then:
        CountingLocator.running() == 0
    }

    void "a refresh reads the bootstrap configuration again with the bootstrap context it reuses"() {
        given: "a bootstrap configuration file"
        Path configDir = Files.createTempDirectory("bootstrap-context-refresh-")
        Path bootstrapFile = configDir.resolve("bootstrap.yml")
        bootstrapFile.toFile().text = "app.bootstrap-value: one"
        ApplicationContext ctx = ApplicationContext.builder()
                .properties(['spec.name': 'BootstrapContextRefreshSpec'])
                .overrideConfigLocations("file:" + configDir)
                .bootstrapEnvironment(true)
                .start()

        expect:
        ctx.getProperty('app.bootstrap-value', String).get() == 'one'

        when:
        bootstrapFile.toFile().text = "app.bootstrap-value: two"
        ctx.environment.refresh()

        then:
        ctx.getProperty('app.bootstrap-value', String).get() == 'two'
        CountingLocator.created() == 1
        CountingLocator.running() == 1

        cleanup:
        ctx.close()
        Files.deleteIfExists(bootstrapFile)
        Files.deleteIfExists(configDir)
    }

    void "a refresh that fails to read the bootstrap configuration keeps the bootstrap context: #failureType.simpleName"() {
        given:
        ApplicationContext ctx = startContext()

        when:
        CountingLocator.failure = failure
        ctx.environment.refresh()

        then: "the bootstrap context of the start is still running"
        thrown(failureType)
        CountingLocator.created() == 1
        CountingLocator.running() == 1

        when:
        ctx.close()

        then:
        CountingLocator.running() == 0

        cleanup:
        ctx.close()

        where: "a locator written in Groovy or Kotlin can throw a checked exception"
        failure                                    | failureType
        new IllegalStateException("cannot be read") | IllegalStateException
        new IOException("cannot be read")           | IOException
    }

    void "a refresh after a refresh that failed to read the bootstrap configuration reads it again: #failureType.simpleName"() {
        given:
        CountingLocator.remoteValue = 'one'
        ApplicationContext ctx = startContext()

        expect:
        ctx.getProperty('app.remote', String).get() == 'one'

        when: "the distributed configuration changes, and the refresh fails to read it"
        CountingLocator.remoteValue = 'two'
        CountingLocator.failure = failure
        ctx.environment.refresh()

        then: "the properties read at the start stay"
        thrown(failureType)
        ctx.getProperty('app.remote', String).get() == 'one'

        when: "the next refresh succeeds"
        CountingLocator.failure = null
        ctx.environment.refresh()

        then: "it reads the distributed configuration again, with one bootstrap context running"
        ctx.getProperty('app.remote', String).get() == 'two'
        CountingLocator.running() == 1

        cleanup:
        ctx.close()

        where:
        failure                                    | failureType
        new IllegalStateException("cannot be read") | IllegalStateException
        new IOException("cannot be read")           | IOException
    }

    void "a start after a stop reads the bootstrap configuration with a new bootstrap context"() {
        given:
        ApplicationContext ctx = startContext()

        when:
        ctx.stop()

        then:
        CountingLocator.running() == 0

        when:
        ctx.start()

        then:
        CountingLocator.created() == 2
        CountingLocator.running() == 1

        cleanup:
        ctx.close()
    }

    void "a refresh keeps the order of the builder properties"() {
        given: "a property source of higher order than the builder properties, which sets the same key"
        ApplicationContext ctx = ApplicationContext.builder()
                .properties(['spec.name': 'BootstrapContextRefreshSpec', 'app.value': 'builder'])
                .propertySources(PropertySource.of('remote', ['app.value': 'remote'], 30))
                .bootstrapEnvironment(true)
                .start()
        int order = contextSource(ctx).order

        expect:
        ctx.getProperty('app.value', String).get() == 'remote'

        when:
        5.times { ctx.environment.refresh() }

        then: "the builder properties do not come to override it"
        contextSource(ctx).order == order
        ctx.getProperty('app.value', String).get() == 'remote'

        cleanup:
        ctx.close()
    }

    void "a refresh reads the bootstrap configuration with new property source loaders"() {
        given: "a property source loader, visible only to this context, that returns its property source on its first call only"
        Path servicesDir = Files.createTempDirectory("bootstrap-context-loaders-")
        Path services = servicesDir.resolve("META-INF/services/" + PropertySourceLoader.name)
        Files.createDirectories(services.parent)
        services.toFile().text = OnceLoader.name
        URLClassLoader classLoader = new URLClassLoader([servicesDir.toUri().toURL()] as URL[], getClass().classLoader)
        ApplicationContext ctx = ApplicationContext.builder()
                .classLoader(classLoader)
                .properties(['spec.name': 'BootstrapContextRefreshSpec'])
                .bootstrapEnvironment(true)
                .start()

        expect:
        ctx.getProperty('once.value', String).get() == 'once'

        when:
        3.times { ctx.environment.refresh() }

        then: "each refresh of the bootstrap environment has new loaders, as when each refresh created a bootstrap context, so the property source is still there"
        ctx.getProperty('once.value', String).get() == 'once'
        CountingLocator.created() == 1

        and: "one loader for the application environment, which keeps it, and one for each read of the bootstrap configuration"
        OnceLoader.instances.get() == 1 + 4

        cleanup:
        ctx.close()
        classLoader.close()
        Files.deleteIfExists(services)
        Files.deleteIfExists(services.parent)
        Files.deleteIfExists(services.parent.parent)
        Files.deleteIfExists(servicesDir)
    }

    private static ApplicationContext startContext() {
        ApplicationContext.builder()
                .properties(['spec.name': 'BootstrapContextRefreshSpec'])
                .bootstrapEnvironment(true)
                .start()
    }

    private static PropertySource contextSource(ApplicationContext ctx) {
        ctx.environment.propertySources.find { it.name == PropertySource.CONTEXT }
    }

    /**
     * A loader that ignores the resource it is asked for and returns its property source on its
     * first call only.
     */
    static class OnceLoader implements PropertySourceLoader {
        static final AtomicInteger instances = new AtomicInteger()
        private boolean loaded

        OnceLoader() {
            instances.incrementAndGet()
        }

        @Override
        Set<String> getExtensions() {
            Set.of('once')
        }

        @Override
        synchronized Optional<PropertySource> load(String resourceName, ResourceLoader resourceLoader) {
            if (loaded) {
                return Optional.empty()
            }
            loaded = true
            return Optional.of(PropertySource.of('once', ['once.value': 'once']))
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

    @BootstrapContextCompatible
    @Singleton
    @Requires(property = "spec.name", value = "BootstrapContextRefreshSpec")
    static class CountingLocator implements BootstrapPropertySourceLocator {
        static final List<CountingLocator> instances = new CopyOnWriteArrayList<>()
        static volatile Throwable failure
        static volatile String remoteValue

        volatile boolean destroyed
        volatile int reads

        CountingLocator() {
            instances.add(this)
        }

        static int created() {
            instances.size()
        }

        static int running() {
            instances.count { !it.destroyed }
        }

        @PreDestroy
        void destroy() {
            destroyed = true
        }

        @Override
        Iterable<PropertySource> findPropertySources(Environment environment) {
            reads++
            if (failure != null) {
                throw failure
            }
            if (remoteValue != null) {
                return List.of(PropertySource.of('remote', ['app.remote': remoteValue]))
            }
            return List.of()
        }
    }
}

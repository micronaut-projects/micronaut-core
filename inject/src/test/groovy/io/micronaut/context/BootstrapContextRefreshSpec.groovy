package io.micronaut.context

import io.micronaut.context.annotation.BootstrapContextCompatible
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.BootstrapPropertySourceLocator
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList

class BootstrapContextRefreshSpec extends Specification {

    def setup() {
        CountingLocator.instances.clear()
        CountingLocator.failure = null
    }

    void "a refresh closes the bootstrap context it replaces, and closing the context closes the last one"() {
        given:
        ApplicationContext ctx = startContext()

        expect: "the start reads the bootstrap configuration with one bootstrap context"
        CountingLocator.created() == 1
        CountingLocator.running() == 1

        when:
        5.times { ctx.environment.refresh() }

        then: "each refresh reads it with a new bootstrap context and closes the previous one"
        CountingLocator.created() == 6
        CountingLocator.running() == 1
        !CountingLocator.instances.last().destroyed

        when:
        ctx.close()

        then:
        CountingLocator.running() == 0
    }

    void "a refresh that fails to read the bootstrap configuration closes the bootstrap context it created: #failureType.simpleName"() {
        given:
        ApplicationContext ctx = startContext()

        when:
        CountingLocator.failure = failure
        ctx.environment.refresh()

        then: "the bootstrap context of the start, whose property sources stay, is still running"
        thrown(failureType)
        CountingLocator.created() == 2
        !CountingLocator.instances[0].destroyed
        CountingLocator.instances[1].destroyed

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

    private static ApplicationContext startContext() {
        ApplicationContext.builder()
                .properties(['spec.name': 'BootstrapContextRefreshSpec'])
                .bootstrapEnvironment(true)
                .start()
    }

    private static PropertySource contextSource(ApplicationContext ctx) {
        ctx.environment.propertySources.find { it.name == PropertySource.CONTEXT }
    }

    @BootstrapContextCompatible
    @Singleton
    @Requires(property = "spec.name", value = "BootstrapContextRefreshSpec")
    static class CountingLocator implements BootstrapPropertySourceLocator {
        static final List<CountingLocator> instances = new CopyOnWriteArrayList<>()
        static volatile Throwable failure

        volatile boolean destroyed

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
            if (failure != null) {
                throw failure
            }
            return List.of()
        }
    }
}

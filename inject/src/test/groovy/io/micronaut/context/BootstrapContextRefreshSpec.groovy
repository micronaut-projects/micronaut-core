package io.micronaut.context

import io.micronaut.context.annotation.BootstrapContextCompatible
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.BootstrapPropertySourceLocator
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BootstrapContextRefreshSpec extends Specification {

    def setup() {
        CountingLocator.created.set(0)
        CountingLocator.running.set(0)
        CountingLocator.fail.set(false)
    }

    void "a refresh closes the bootstrap context it replaces, and closing the context closes the last one"() {
        given:
        ApplicationContext ctx = ApplicationContext.builder()
                .properties(['spec.name': 'BootstrapContextRefreshSpec'])
                .bootstrapEnvironment(true)
                .start()

        expect: "the start reads the bootstrap configuration with one bootstrap context"
        CountingLocator.created.get() == 1
        CountingLocator.running.get() == 1

        when:
        5.times { ctx.environment.refresh() }

        then: "each refresh reads it with a new bootstrap context and closes the previous one"
        CountingLocator.created.get() == 6
        CountingLocator.running.get() == 1

        when:
        ctx.close()

        then:
        CountingLocator.running.get() == 0
    }

    void "a refresh that fails to read the bootstrap configuration closes the bootstrap context it created"() {
        given:
        ApplicationContext ctx = ApplicationContext.builder()
                .properties(['spec.name': 'BootstrapContextRefreshSpec'])
                .bootstrapEnvironment(true)
                .start()

        when:
        CountingLocator.fail.set(true)
        ctx.environment.refresh()

        then: "the bootstrap context of the start, whose property sources stay, is still running"
        thrown(IllegalStateException)
        CountingLocator.created.get() == 2
        CountingLocator.running.get() == 1

        when:
        ctx.close()

        then:
        CountingLocator.running.get() == 0
    }

    @BootstrapContextCompatible
    @Singleton
    @Requires(property = "spec.name", value = "BootstrapContextRefreshSpec")
    static class CountingLocator implements BootstrapPropertySourceLocator {
        static final AtomicInteger created = new AtomicInteger()
        static final AtomicInteger running = new AtomicInteger()
        static final AtomicBoolean fail = new AtomicBoolean()

        CountingLocator() {
            created.incrementAndGet()
            running.incrementAndGet()
        }

        @PreDestroy
        void destroy() {
            running.decrementAndGet()
        }

        @Override
        Iterable<PropertySource> findPropertySources(Environment environment) {
            if (fail.get()) {
                throw new IllegalStateException("the bootstrap configuration cannot be read")
            }
            return List.of()
        }
    }
}

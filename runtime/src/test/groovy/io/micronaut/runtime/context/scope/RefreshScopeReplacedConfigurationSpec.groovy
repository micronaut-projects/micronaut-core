package io.micronaut.runtime.context.scope

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

/**
 * A configuration bean can also implement a type that a {@link BeanCreatedEventListener} replaces with an instance
 * of another type, for example a datasource configuration that is also the {@code DataSource} and gets wrapped by a
 * {@code DataSource} listener. The bean registration then holds the replacement, which is not an instance of the
 * configuration class, so the refresh scope can not re-bind the configuration into it. It used to fail the whole
 * refresh with a {@link ClassCastException}; now it skips that bean and refreshes the other configuration beans.
 */
@RestoreSystemProperties
class RefreshScopeReplacedConfigurationSpec extends Specification {

    void "a configuration bean replaced by another type does not fail the refresh"() {
        given:
        System.setProperty("replaced.name", "first")
        System.setProperty("other.name", "first")
        ApplicationContext context = ApplicationContext.run(['spec.name': 'RefreshScopeReplacedConfigurationSpec'])
        Greeter greeter = context.getBean(Greeter)
        OtherConfig otherConfig = context.getBean(OtherConfig)

        expect: "the listener replaced the ReplacedConfig bean with a WrappingGreeter, which is not a ReplacedConfig"
        greeter instanceof WrappingGreeter
        !(greeter instanceof ReplacedConfig)
        greeter.greet() == "Hello first"

        when: "a refresh event with changed keys, including a key of the replaced bean, is published"
        System.setProperty("replaced.name", "second")
        System.setProperty("other.name", "second")
        context.publishEvent(new RefreshEvent(context.environment.refreshAndDiff()))

        then: "the refresh does not fail with a ClassCastException"
        noExceptionThrown()

        and: "the replaced bean can not be re-bound, so it is skipped and keeps its previous value"
        greeter.greet() == "Hello first"

        and: "the other configuration beans are still refreshed"
        otherConfig.name == "second"

        when: "a full refresh event (new RefreshEvent()) is published, which re-binds all configuration beans"
        System.setProperty("replaced.name", "third")
        System.setProperty("other.name", "third")
        context.environment.refresh()
        context.publishEvent(new RefreshEvent())

        then: "the same applies to the full refresh"
        noExceptionThrown()
        greeter.greet() == "Hello first"
        otherConfig.name == "third"

        cleanup:
        context.close()
    }

    interface Greeter {
        String greet()
    }

    /**
     * The configuration bean, which is also a {@link Greeter}.
     */
    @Requires(property = "spec.name", value = "RefreshScopeReplacedConfigurationSpec")
    @ConfigurationProperties("replaced")
    static class ReplacedConfig implements Greeter {
        String name

        @Override
        String greet() {
            return "Hello " + name
        }
    }

    /**
     * A regular configuration bean, refreshed in the same refresh events.
     */
    @Requires(property = "spec.name", value = "RefreshScopeReplacedConfigurationSpec")
    @ConfigurationProperties("other")
    static class OtherConfig {
        String name
    }

    /**
     * Replaces every {@link Greeter} bean, including the {@link ReplacedConfig} bean, with a {@link WrappingGreeter}.
     */
    @Requires(property = "spec.name", value = "RefreshScopeReplacedConfigurationSpec")
    @Singleton
    static class GreeterWrapper implements BeanCreatedEventListener<Greeter> {
        @Override
        Greeter onCreated(BeanCreatedEvent<Greeter> event) {
            return new WrappingGreeter(event.bean)
        }
    }

    /**
     * A wrapper of another type than the configuration class, delegating to the wrapped bean.
     */
    static class WrappingGreeter implements Greeter {
        final Greeter target

        WrappingGreeter(Greeter target) {
            this.target = target
        }

        @Override
        String greet() {
            return target.greet()
        }
    }
}

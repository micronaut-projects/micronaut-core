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

@RestoreSystemProperties
class RefreshScopeReplacedConfigurationSpec extends Specification {

    void "a configuration bean replaced by another type does not fail the refresh"() {
        given:
        System.setProperty("replaced.name", "first")
        System.setProperty("other.name", "first")
        ApplicationContext context = ApplicationContext.run(['spec.name': 'RefreshScopeReplacedConfigurationSpec'])
        Greeter greeter = context.getBean(Greeter)
        OtherConfig otherConfig = context.getBean(OtherConfig)

        expect: "the configuration bean was replaced by the listener"
        greeter instanceof WrappingGreeter
        greeter.greet() == "Hello first"

        when:
        System.setProperty("replaced.name", "second")
        System.setProperty("other.name", "second")
        context.publishEvent(new RefreshEvent(context.environment.refreshAndDiff()))

        then: "the replaced bean is skipped and the other configuration beans are refreshed"
        noExceptionThrown()
        otherConfig.name == "second"

        when:
        System.setProperty("other.name", "third")
        context.environment.refresh()
        context.publishEvent(new RefreshEvent())

        then:
        noExceptionThrown()
        otherConfig.name == "third"

        cleanup:
        context.close()
    }

    interface Greeter {
        String greet()
    }

    @Requires(property = "spec.name", value = "RefreshScopeReplacedConfigurationSpec")
    @ConfigurationProperties("replaced")
    static class ReplacedConfig implements Greeter {
        String name

        @Override
        String greet() {
            return "Hello " + name
        }
    }

    @Requires(property = "spec.name", value = "RefreshScopeReplacedConfigurationSpec")
    @ConfigurationProperties("other")
    static class OtherConfig {
        String name
    }

    @Requires(property = "spec.name", value = "RefreshScopeReplacedConfigurationSpec")
    @Singleton
    static class GreeterWrapper implements BeanCreatedEventListener<Greeter> {
        @Override
        Greeter onCreated(BeanCreatedEvent<Greeter> event) {
            return new WrappingGreeter(event.bean)
        }
    }

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

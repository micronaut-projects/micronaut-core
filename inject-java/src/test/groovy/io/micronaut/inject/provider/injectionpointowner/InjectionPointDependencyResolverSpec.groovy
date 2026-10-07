package io.micronaut.inject.provider.injectionpointowner

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.DefaultBeanDefinitionsProvider
import io.micronaut.core.type.Argument
import io.micronaut.inject.beans.injectionpoints.DisposableDependency
import spock.lang.AutoCleanup
import spock.lang.Specification

class InjectionPointDependencyResolverSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.builder()
            .properties("spec": getClass().getSimpleName())
            .beanDefinitionsProvider { ClassLoader classLoader ->
                new DefaultBeanDefinitionsProvider().provide(classLoader) + [new OwnedDefinition()]
            }
            .start()

    void "a dependent resolved for an injection point is owned by the bean it is injected into"() {
        when:
        BeanRegistration<OwningConsumer> registration = context.getBeanRegistration(OwningConsumer, null)
        DisposableDependency constructed = registration.bean.constructed.registration().bean
        DisposableDependency injected = registration.bean.injected.registration().bean

        then:
        !constructed.destroyed
        !injected.destroyed

        when:
        registration.close()

        then:
        constructed.destroyed
        injected.destroyed
    }

    void "a dependent resolved for an injection point is destroyed early through the resolver"() {
        given:
        BeanRegistration<OwningConsumer> registration = context.getBeanRegistration(OwningConsumer, null)
        Owned<Integer> owned = registration.bean.constructed

        expect:
        owned.resolver().destroy(owned.registration())
        owned.registration().bean.destroyed
        !registration.bean.injected.registration().bean.destroyed

        and: 'the resolver of one injection point owns what the other resolved, as both are owned by the consumer'
        owned.resolver().destroy(registration.bean.injected.registration())
        registration.bean.injected.registration().bean.destroyed

        cleanup:
        registration.close()
    }

    void "a looked up bean owns what it resolves itself"() {
        when:
        BeanRegistration<Owned<String>> registration = context.getBeanRegistration(Argument.of(Owned, String), null)
        DisposableDependency dependency = registration.bean.registration().bean

        then:
        !dependency.destroyed

        when:
        registration.close()

        then:
        dependency.destroyed
    }
}

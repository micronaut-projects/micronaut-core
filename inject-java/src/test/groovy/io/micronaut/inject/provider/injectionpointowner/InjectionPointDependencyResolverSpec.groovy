package io.micronaut.inject.provider.injectionpointowner

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.DefaultBeanDefinitionsProvider
import io.micronaut.core.type.Argument
import io.micronaut.inject.beans.injectionpoints.DisposableDependency
import io.micronaut.inject.qualifiers.Qualifiers
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

    void "a dependent resolved for an injection point is owned by the each bean delegate it is injected into"() {
        when:
        BeanRegistration<EachSeedConsumer> one = context.getBeanRegistration(EachSeedConsumer, Qualifiers.byName("one"))
        BeanRegistration<EachSeedConsumer> two = context.getBeanRegistration(EachSeedConsumer, Qualifiers.byName("two"))

        then:
        one.bean.seed.name() == "one"
        two.bean.seed.name() == "two"
        !one.bean.constructed.registration().bean.is(two.bean.constructed.registration().bean)
        !one.bean.injected.registration().bean.is(two.bean.injected.registration().bean)
        !one.bean.constructed.registration().bean.destroyed
        !one.bean.injected.registration().bean.destroyed

        when:
        context.destroyBean(one)

        then:
        one.bean.constructed.registration().bean.destroyed
        one.bean.injected.registration().bean.destroyed
        !two.bean.constructed.registration().bean.destroyed
        !two.bean.injected.registration().bean.destroyed

        when:
        context.destroyBean(two)

        then:
        two.bean.constructed.registration().bean.destroyed
        two.bean.injected.registration().bean.destroyed
    }

    void "the resolvers of two each bean delegates destroy only what each of them owns"() {
        given:
        EachSeedConsumer one = context.getBean(EachSeedConsumer, Qualifiers.byName("one"))
        EachSeedConsumer two = context.getBean(EachSeedConsumer, Qualifiers.byName("two"))

        expect:
        !one.constructed.resolver().destroy(two.constructed.registration())
        !one.constructed.resolver().destroy(two.injected.registration())
        !two.constructed.registration().bean.destroyed
        !two.injected.registration().bean.destroyed

        and: 'the resolver of one injection point owns what the other resolved for the same delegate'
        one.constructed.resolver().destroy(one.injected.registration())
        one.injected.registration().bean.destroyed
        two.injected.resolver().destroy(two.constructed.registration())
        two.constructed.registration().bean.destroyed
        !one.constructed.registration().bean.destroyed
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

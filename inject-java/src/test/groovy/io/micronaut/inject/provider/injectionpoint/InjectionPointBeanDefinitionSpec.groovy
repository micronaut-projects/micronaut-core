package io.micronaut.inject.provider.injectionpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanDefinitionsProvider
import io.micronaut.context.Qualifier
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.AnyQualifier
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Specification

class InjectionPointBeanDefinitionSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.builder()
            .properties("spec": getClass().getSimpleName())
            .beanDefinitionsProvider { ClassLoader classLoader ->
                new DefaultBeanDefinitionsProvider().provide(classLoader) + [new InjectedAtDefinition()]
            }
            .start()

    void "the bean is built for the field, constructor argument and method argument it is injected into"() {
        when:
        Consumer consumer = context.getBean(Consumer)

        then:
        consumer.greeting.name() == "greeting"
        consumer.greeting.type().type == String
        consumer.count.name() == "count"
        consumer.count.type().type == Integer
        consumer.total.name() == "total"
        consumer.total.type().type == Long
    }

    void "the bean is a candidate for an injection point of any qualifier, and one is built for each injection point"() {
        when:
        Consumer consumer = context.getBean(Consumer)

        then:
        consumer.qualified.name() == "qualified"
        !consumer.qualified.is(consumer.greeting)
    }

    void "a bean looked up from the context is built for the type that was looked up"() {
        when:
        InjectedAt<UUID> lookedUp = context.getBean(Argument.of(InjectedAt, UUID))
        InjectedAt<UUID> qualified = context.getBean(Argument.of(InjectedAt, UUID), Qualifiers.byName("any"))

        then:
        lookedUp.type().type == UUID
        qualified.type().type == UUID
        !lookedUp.is(qualified)
    }

    void "a definition instantiated outside of any resolution has no injection point"() {
        given:
        InjectedAtDefinition definition = new InjectedAtDefinition()

        when:
        InjectedAt<?> built = definition.instantiate(context)

        then:
        built.name() == InjectedAtDefinition.NO_INJECTION_POINT
        built.type() == Argument.OBJECT_ARGUMENT
    }

    void "the definition is its own reference, of any qualifier and one type variable"() {
        given:
        BeanDefinition<?> definition = context.getBeanDefinition(Argument.of(InjectedAt, String))

        expect:
        definition instanceof InjectedAtDefinition
        definition == new InjectedAtDefinition()
        definition.hashCode() == new InjectedAtDefinition().hashCode()
        definition.load().is(definition)
        definition.beanDefinitionName == InjectedAtDefinition.name
        definition.present
        definition.isEnabled(context)
        !definition.singleton
        !definition.abstract
        !definition.containerType
        !definition.configurationProperties
        definition.declaredQualifier == (Qualifier) AnyQualifier.INSTANCE
        definition.typeArguments*.name == ["T"]
        definition.getTypeArguments(InjectedAt)*.name == ["T"]
        definition.getTypeArguments(Object).isEmpty()
    }
}

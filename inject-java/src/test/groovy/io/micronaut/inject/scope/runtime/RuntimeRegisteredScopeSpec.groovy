package io.micronaut.inject.scope.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.scope.CustomScope
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.function.Supplier

class RuntimeRegisteredScopeSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    void "a custom scope registered at runtime applies to a bean that was resolved before the registration"() {
        when: "the bean is resolved while no scope for its annotation exists"
        def first = context.getBean(RuntimeRegisteredBean)
        def second = context.getBean(RuntimeRegisteredBean)

        then: "it is created as a dependent bean"
        !first.is(second)

        when: "a scope for the annotation is registered at runtime"
        def scope = new RuntimeRegisteredScope()
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(CustomScope, { -> scope } as Supplier<CustomScope>)
            .typeArguments(Argument.of(RuntimeRegistered))
            .singleton(true)
            .build())

        then: "the scope itself is visible to the locator"
        context.getBean(CustomScope, Qualifiers.byTypeArguments(RuntimeRegistered)).is(scope)

        when: "the bean is resolved again"
        def third = context.getBean(RuntimeRegisteredBean)
        def fourth = context.getBean(RuntimeRegisteredBean)

        then: "it goes through the scope"
        scope.created == 1
        third.is(fourth)
    }

    void "a custom scope registered at runtime applies to a bean first resolved after the registration"() {
        given:
        def scope = new RuntimeRegisteredScope()
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(CustomScope, { -> scope } as Supplier<CustomScope>)
            .typeArguments(Argument.of(RuntimeRegistered))
            .singleton(true)
            .build())

        when:
        def first = context.getBean(RuntimeRegisteredBean)
        def second = context.getBean(RuntimeRegisteredBean)

        then:
        scope.created == 1
        first.is(second)
    }
}

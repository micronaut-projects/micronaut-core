package io.micronaut.inject.beans

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.type.Argument
import io.micronaut.inject.beans.injectionpoints.Colour
import io.micronaut.inject.beans.injectionpoints.DisposableDependency
import io.micronaut.inject.beans.injectionpoints.DisposableSingletonDependency
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

import java.util.function.Function

class RuntimeBeanDefinitionInjectionPointSpec extends Specification {

    private static RuntimeBeanDefinition.Builder<Holder> holderBuilder(
            Function<RuntimeBeanDefinition.CreationContext, Holder> factory) {
        RuntimeBeanDefinition.builder(Holder, factory)
    }

    void 'test a compiled bean is resolved for a declared injection point'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        holderBuilder(ctx -> new Holder(ctx.getInjectedBean(0)))
                                .injectionPoint(Argument.of(DisposableSingletonDependency))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        expect:
        context.getBean(Holder).injected.is(context.getBean(DisposableSingletonDependency))

        cleanup:
        context.close()
    }

    void 'test a prototype resolved for an injection point is destroyed with the synthetic bean'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        holderBuilder(ctx -> new Holder(ctx.getInjectedBean(0)))
                                .injectionPoint(Argument.of(DisposableDependency))
                                .scope(Prototype)
                                .build()
                )
                .build()
                .start()

        when:
        BeanRegistration<Holder> registration = context.getBeanRegistration(Holder, null)
        DisposableDependency dependency = registration.bean.injected as DisposableDependency

        then: 'the prototype was resolved through the resolution context'
        dependency != null
        !dependency.destroyed

        when:
        registration.close()

        then: 'core destroys it as a dependent of the synthetic bean'
        dependency.destroyed

        cleanup:
        context.close()
    }

    void 'test a prototype resolved for an injection point of a synthetic singleton is destroyed with it'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        holderBuilder(ctx -> new Holder(ctx.getInjectedBean(0)))
                                .injectionPoint(Argument.of(DisposableDependency))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()
        Holder holder = context.getBean(Holder)
        DisposableDependency dependency = holder.injected as DisposableDependency

        when:
        context.destroyBean(holder)

        then:
        dependency.destroyed

        cleanup:
        context.close()
    }

    void 'test a singleton resolved for an injection point is not destroyed with the synthetic bean'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        holderBuilder(ctx -> new Holder(ctx.getInjectedBean(0)))
                                .injectionPoint(Argument.of(DisposableSingletonDependency))
                                .scope(Prototype)
                                .build()
                )
                .build()
                .start()

        when:
        BeanRegistration<Holder> registration = context.getBeanRegistration(Holder, null)
        def dependency = registration.bean.injected as DisposableSingletonDependency
        registration.close()

        then:
        !dependency.destroyed

        when:
        context.close()

        then:
        dependency.destroyed
    }

    void 'test a qualified injection point selects the compiled bean with that qualifier'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        holderBuilder(ctx ->
                                new Holder(ctx.getInjectedBean(Argument.of(Colour), Qualifiers.byName("green"))))
                                .injectionPoint(Argument.of(Colour), Qualifiers.byName("green"))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        expect:
        (context.getBean(Holder).injected as Colour).name() == "green"

        cleanup:
        context.close()
    }

    static class Holder {
        final Object injected

        Holder(Object injected) {
            this.injected = injected
        }
    }
}

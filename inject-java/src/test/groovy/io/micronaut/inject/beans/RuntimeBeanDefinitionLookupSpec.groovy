package io.micronaut.inject.beans

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.exceptions.CircularDependencyException
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.core.type.Argument
import io.micronaut.inject.beans.injectionpoints.Colour
import io.micronaut.inject.beans.injectionpoints.DisposableDependency
import io.micronaut.inject.beans.injectionpoints.DisposableSingletonDependency
import io.micronaut.inject.beans.lookups.CircularDependency
import io.micronaut.inject.beans.lookups.LookupHolder
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

import java.util.function.BiConsumer
import java.util.function.Function

class RuntimeBeanDefinitionLookupSpec extends Specification {

    private static RuntimeBeanDefinition.Builder<LookupHolder> holderBuilder(
            Function<RuntimeBeanDefinition.CreationContext, LookupHolder> factory) {
        RuntimeBeanDefinition.builder(LookupHolder, factory)
    }

    void 'test a compiled bean can be looked up by the creator'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        holderBuilder(ctx -> new LookupHolder(ctx.getBean(Argument.of(DisposableSingletonDependency))))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        expect:
        context.getBean(LookupHolder).created.is(context.getBean(DisposableSingletonDependency))

        cleanup:
        context.close()
    }

    void 'test a qualified compiled bean can be looked up by the creator'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        holderBuilder(ctx ->
                                new LookupHolder(ctx.getBean(Argument.of(Colour), Qualifiers.byName("red"))))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        expect:
        (context.getBean(LookupHolder).created as Colour).name() == "red"

        cleanup:
        context.close()
    }

    void 'test a prototype the creator looked up is destroyed with the created bean and not before'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        holderBuilder(ctx -> new LookupHolder(ctx.getBean(Argument.of(DisposableDependency))))
                                .scope(Prototype)
                                .build()
                )
                .build()
                .start()

        when:
        BeanRegistration<LookupHolder> registration = context.getBeanRegistration(LookupHolder, null)
        DisposableDependency dependency = registration.bean.created as DisposableDependency

        then: 'the lookup went through the resolution context of the creation'
        dependency != null
        !dependency.destroyed

        when:
        registration.close()

        then: 'core destroyed it as a dependent of the created bean'
        dependency.destroyed

        cleanup:
        context.close()
    }

    void 'test a prototype the creator of a singleton looked up is destroyed with it'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        holderBuilder(ctx -> new LookupHolder(ctx.getBean(Argument.of(DisposableDependency))))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()
        LookupHolder holder = context.getBean(LookupHolder)
        DisposableDependency dependency = holder.created as DisposableDependency

        when:
        context.destroyBean(holder)

        then:
        dependency.destroyed

        cleanup:
        context.close()
    }

    void 'test a singleton the creator looked up is not destroyed with the created bean'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        holderBuilder(ctx -> new LookupHolder(ctx.getBean(Argument.of(DisposableSingletonDependency))))
                                .scope(Prototype)
                                .build()
                )
                .build()
                .start()

        when:
        BeanRegistration<LookupHolder> registration = context.getBeanRegistration(LookupHolder, null)
        def dependency = registration.bean.created as DisposableSingletonDependency
        registration.close()

        then:
        !dependency.destroyed

        when:
        context.close()

        then:
        dependency.destroyed
    }

    void 'test a prototype the disposer looked up is destroyed when the disposer returns'() {
        given:
        def resolvedByDisposer = []
        def destroyedInsideDisposer = []
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        holderBuilder(ctx -> new LookupHolder(ctx.getBean(Argument.of(DisposableDependency))))
                                .singleton(true)
                                .injectedDisposer((BiConsumer<RuntimeBeanDefinition.DisposalContext, LookupHolder>) { ctx, bean ->
                                    def dependency = ctx.getBean(Argument.of(DisposableDependency))
                                    resolvedByDisposer << dependency
                                    destroyedInsideDisposer << dependency.destroyed
                                })
                                .build()
                )
                .build()
                .start()
        LookupHolder holder = context.getBean(LookupHolder)
        DisposableDependency createdWith = holder.created as DisposableDependency

        when:
        context.destroyBean(holder)
        DisposableDependency disposedWith = resolvedByDisposer[0] as DisposableDependency

        then: 'the disposer resolved an instance of its own, not the one the creator got'
        !disposedWith.is(createdWith)

        and: 'it was still alive while the disposer ran and destroyed once it returned'
        !destroyedInsideDisposer[0]
        disposedWith.destroyed

        and: 'the instance the creator got is destroyed with the bean as before'
        createdWith.destroyed

        cleanup:
        context.close()
    }

    void 'test a singleton the disposer looked up is not destroyed when the disposer returns'() {
        given:
        def resolvedByDisposer = []
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        holderBuilder(ctx -> new LookupHolder(null))
                                .singleton(true)
                                .injectedDisposer((BiConsumer<RuntimeBeanDefinition.DisposalContext, LookupHolder>) { ctx, bean ->
                                    resolvedByDisposer << ctx.getBean(Argument.of(DisposableSingletonDependency))
                                })
                                .build()
                )
                .build()
                .start()

        when:
        context.destroyBean(context.getBean(LookupHolder))
        def dependency = resolvedByDisposer[0] as DisposableSingletonDependency

        then:
        dependency.is(context.getBean(DisposableSingletonDependency))
        !dependency.destroyed

        when:
        context.close()

        then:
        dependency.destroyed
    }

    void 'test the disposer runs with its lookups when the context is closed'() {
        given:
        def resolvedByDisposer = []
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        holderBuilder(ctx -> new LookupHolder(null))
                                .singleton(true)
                                .injectedDisposer((BiConsumer<RuntimeBeanDefinition.DisposalContext, LookupHolder>) { ctx, bean ->
                                    resolvedByDisposer << ctx.getBean(Argument.of(DisposableDependency))
                                })
                                .build()
                )
                .build()
                .start()
        context.getBean(LookupHolder)

        when:
        context.close()

        then:
        resolvedByDisposer.size() == 1
        (resolvedByDisposer[0] as DisposableDependency).destroyed
    }

    void 'test what the disposer resolved is destroyed even when the disposer fails'() {
        given:
        def resolvedByDisposer = []
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        holderBuilder(ctx -> new LookupHolder(null))
                                .singleton(true)
                                .injectedDisposer((BiConsumer<RuntimeBeanDefinition.DisposalContext, LookupHolder>) { ctx, bean ->
                                    resolvedByDisposer << ctx.getBean(Argument.of(DisposableDependency))
                                    ctx.getBean(Argument.of(NotABean))
                                })
                                .build()
                )
                .build()
                .start()

        when: 'the disposer fails on a lookup that cannot be satisfied'
        context.destroyBean(context.getBean(LookupHolder))

        then: 'the failure does not propagate, the way it does not for any other disposer'
        noExceptionThrown()

        and: 'what it resolved before failing is destroyed all the same'
        (resolvedByDisposer[0] as DisposableDependency).destroyed

        cleanup:
        context.close()
    }

    void 'test a circular lookup is detected'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        holderBuilder(ctx -> new LookupHolder(ctx.getBean(Argument.of(CircularDependency))))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        when:
        context.getBean(LookupHolder)

        then:
        thrown(CircularDependencyException)

        cleanup:
        context.close()
    }

    void 'test an unsatisfied lookup by the creator fails the creation'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        holderBuilder(ctx -> new LookupHolder(ctx.getBean(Argument.of(NotABean))))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        when:
        context.getBean(LookupHolder)

        then:
        def e = thrown(DependencyInjectionException)
        e.message.contains(NotABean.name)

        cleanup:
        context.close()
    }

    static class NotABean {
    }
}

package io.micronaut.inject.beans

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanDependencyResolver
import io.micronaut.context.BeanRegistration
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.type.Argument
import io.micronaut.inject.beans.injectionpoints.DisposableDependency
import io.micronaut.inject.beans.injectionpoints.DisposableSingletonDependency
import io.micronaut.inject.beans.lookups.LookupHolder
import spock.lang.Specification

import java.util.function.BiConsumer
import java.util.function.Function

class RuntimeBeanDefinitionDependenciesSpec extends Specification {

    private static RuntimeBeanDefinition.Builder<LookupHolder> holderBuilder(
            Function<RuntimeBeanDefinition.CreationContext, LookupHolder> factory) {
        RuntimeBeanDefinition.builder(LookupHolder, factory)
    }

    private static ApplicationContext start(RuntimeBeanDefinition<?> definition) {
        ApplicationContext.builder()
                .beanDefinitions(definition)
                .build()
                .start()
    }

    void 'test a dependent the creator resolved is destroyed when the runtime bean is destroyed'() {
        given:
        def context = start(holderBuilder(ctx -> new LookupHolder(ctx.dependencies.getBean(DisposableDependency)))
                .scope(Prototype)
                .build())

        when:
        BeanRegistration<LookupHolder> registration = context.getBeanRegistration(LookupHolder, null)
        def dependency = registration.bean.created as DisposableDependency

        then:
        !dependency.destroyed

        when:
        registration.close()

        then:
        dependency.destroyed

        cleanup:
        context.close()
    }

    void 'test a dependent the creator of a singleton resolved is destroyed with it and when the context closes'() {
        given:
        def context = start(holderBuilder(ctx -> new LookupHolder(ctx.dependencies.getBean(DisposableDependency)))
                .singleton(true)
                .build())

        when:
        def dependency = context.getBean(LookupHolder).created as DisposableDependency

        then:
        !dependency.destroyed

        when:
        context.close()

        then:
        dependency.destroyed
    }

    void 'test the creator resolves the registration of an exact definition'() {
        given:
        def context = start(holderBuilder(ctx -> {
            def definition = ctx.beanContext.getBeanDefinition(DisposableDependency)
            new LookupHolder(ctx.dependencies.getBeanRegistration(definition, Argument.of(DisposableDependency)))
        }).scope(Prototype).build())

        when:
        BeanRegistration<LookupHolder> registration = context.getBeanRegistration(LookupHolder, null)
        def dependency = (registration.bean.created as BeanRegistration<DisposableDependency>).bean

        then:
        !dependency.destroyed

        when:
        registration.close()

        then:
        dependency.destroyed

        cleanup:
        context.close()
    }

    void 'test a dependent the creator resolved is destroyed early through the resolver'() {
        given:
        def context = start(holderBuilder(ctx -> {
            BeanDependencyResolver dependencies = ctx.dependencies
            new LookupHolder([dependencies, dependencies.getBeanRegistration(DisposableDependency)])
        }).scope(Prototype).build())
        BeanRegistration<LookupHolder> registration = context.getBeanRegistration(LookupHolder, null)
        def (BeanDependencyResolver resolver, BeanRegistration<DisposableDependency> owned) = registration.bean.created as List

        when:
        boolean destroyed = resolver.destroy(owned)

        then:
        destroyed
        owned.bean.destroyed

        and: 'the resolver no longer owns it'
        !resolver.destroy(owned)

        when:
        registration.close()

        then:
        owned.bean.destroyed

        cleanup:
        context.close()
    }

    void 'test the resolver never destroys a shared registration'() {
        given:
        def context = start(holderBuilder(ctx -> {
            BeanDependencyResolver dependencies = ctx.dependencies
            new LookupHolder([dependencies, dependencies.getBeanRegistration(DisposableSingletonDependency)])
        }).scope(Prototype).build())
        BeanRegistration<LookupHolder> registration = context.getBeanRegistration(LookupHolder, null)
        def (BeanDependencyResolver resolver, BeanRegistration<DisposableSingletonDependency> shared) = registration.bean.created as List

        when:
        boolean destroyed = resolver.destroy(shared)
        registration.close()

        then:
        !destroyed
        !shared.bean.destroyed

        when:
        context.close()

        then:
        shared.bean.destroyed
    }

    void 'test a resolver the bean retains owns what it resolves after the creation'() {
        given:
        def context = start(holderBuilder(ctx -> new LookupHolder(ctx.dependencies))
                .scope(Prototype)
                .build())
        BeanRegistration<LookupHolder> registration = context.getBeanRegistration(LookupHolder, null)
        def resolver = registration.bean.created as BeanDependencyResolver

        when:
        def dependency = resolver.getBean(DisposableDependency)

        then:
        !dependency.destroyed

        when:
        registration.close()

        then:
        dependency.destroyed

        when:
        resolver.getBean(DisposableDependency)

        then:
        thrown(IllegalStateException)

        cleanup:
        context.close()
    }

    void 'test what the creator resolved is destroyed when the creation fails'() {
        given:
        def resolved = []
        def context = start(holderBuilder(ctx -> {
            resolved << ctx.dependencies.getBean(DisposableDependency)
            throw new IllegalStateException("bad")
        }).scope(Prototype).build())

        when:
        context.getBean(LookupHolder)

        then:
        thrown(Exception)
        resolved.size() == 1
        (resolved[0] as DisposableDependency).destroyed

        cleanup:
        context.close()
    }

    void 'test a dependent the disposer resolved is destroyed after the disposal'() {
        given:
        def resolvedByDisposer = []
        def destroyedInsideDisposer = []
        def context = start(holderBuilder(ctx -> new LookupHolder(null))
                .singleton(true)
                .injectedDisposer((BiConsumer<RuntimeBeanDefinition.DisposalContext, LookupHolder>) { ctx, bean ->
                    BeanRegistration<DisposableDependency> registration = ctx.dependencies.getBeanRegistration(DisposableDependency)
                    resolvedByDisposer << registration.bean
                    destroyedInsideDisposer << registration.bean.destroyed
                })
                .build())

        when:
        context.destroyBean(context.getBean(LookupHolder))

        then:
        resolvedByDisposer.size() == 1
        !destroyedInsideDisposer[0]
        (resolvedByDisposer[0] as DisposableDependency).destroyed

        cleanup:
        context.close()
    }

    void 'test a dependent the disposer resolved is destroyed after the disposal when the context closes'() {
        given:
        def resolvedByDisposer = []
        def context = start(holderBuilder(ctx -> new LookupHolder(null))
                .singleton(true)
                .injectedDisposer((BiConsumer<RuntimeBeanDefinition.DisposalContext, LookupHolder>) { ctx, bean ->
                    resolvedByDisposer << ctx.dependencies.getBean(DisposableDependency)
                })
                .build())
        context.getBean(LookupHolder)

        when:
        context.close()

        then:
        resolvedByDisposer.size() == 1
        (resolvedByDisposer[0] as DisposableDependency).destroyed
    }

    void 'test the disposer destroys a dependent early through its resolver'() {
        given:
        def results = []
        def context = start(holderBuilder(ctx -> new LookupHolder(null))
                .singleton(true)
                .injectedDisposer((BiConsumer<RuntimeBeanDefinition.DisposalContext, LookupHolder>) { ctx, bean ->
                    def dependencies = ctx.dependencies
                    BeanRegistration<DisposableDependency> registration = dependencies.getBeanRegistration(DisposableDependency)
                    results << dependencies.is(ctx.dependencies)
                    results << dependencies.destroy(registration)
                    results << registration.bean.destroyed
                })
                .build())

        when:
        context.destroyBean(context.getBean(LookupHolder))

        then:
        results == [true, true, true]

        cleanup:
        context.close()
    }

    void 'test the dependencies of the creation are not available when the definition is instantiated outside a creation'() {
        given:
        def failures = []
        def definition = holderBuilder(ctx -> {
            try {
                ctx.dependencies
            } catch (UnsupportedOperationException e) {
                failures << e
            }
            new LookupHolder(null)
        }).build()
        def context = ApplicationContext.run()

        when:
        definition.instantiate(context)

        then:
        failures.size() == 1

        cleanup:
        context.close()
    }
}

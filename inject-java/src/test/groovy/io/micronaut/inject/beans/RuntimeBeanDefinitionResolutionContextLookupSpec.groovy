package io.micronaut.inject.beans

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.context.BeanRegistration
import io.micronaut.context.BeanResolutionContext
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.core.type.Argument
import io.micronaut.inject.beans.injectionpoints.DisposableDependency
import spock.lang.Specification

import java.util.function.BiConsumer
import java.util.function.Function

/**
 * A bean the caller chose by definition is resolved within the caller's resolution context: what is created for it
 * sees the caller's path and attributes, and a dependent it creates belongs to the caller's registration.
 */
class RuntimeBeanDefinitionResolutionContextLookupSpec extends Specification {

    private static final String CALLER_ATTRIBUTE = "caller"

    private static RuntimeBeanDefinition<Chosen> chosenDefinition() {
        RuntimeBeanDefinition.builder(Chosen, { RuntimeBeanDefinition.CreationContext ctx ->
            BeanResolutionContext resolutionContext = ctx.resolutionContext
            new Chosen(
                    resolutionContext.getAttribute(CALLER_ATTRIBUTE),
                    resolutionContext.rootDefinition?.beanType,
                    resolutionContext.path.collect { it.declaringType.beanType },
                    ctx.getBean(Argument.of(DisposableDependency))
            )
        } as Function<RuntimeBeanDefinition.CreationContext, Chosen>)
                .scope(Prototype)
                .disposer({ BeanContext bc, Chosen chosen -> chosen.disposed = true } as BiConsumer<BeanContext, Chosen>)
                .build()
    }

    private static RuntimeBeanDefinition<Outer> outerDefinition(Function<RuntimeBeanDefinition.CreationContext, BeanRegistration<Chosen>> resolve) {
        RuntimeBeanDefinition.builder(Outer, { RuntimeBeanDefinition.CreationContext ctx ->
            ctx.resolutionContext.setAttribute(CALLER_ATTRIBUTE, "outer")
            new Outer(resolve.apply(ctx))
        } as Function<RuntimeBeanDefinition.CreationContext, Outer>)
                .scope(Prototype)
                .build()
    }

    private static ApplicationContext start(RuntimeBeanDefinition<?>... definitions) {
        ApplicationContext.builder()
                .beanDefinitions(definitions)
                .build()
                .start()
    }

    void 'test a bean resolved by definition in the caller resolution context sees the caller path and attributes'() {
        given:
        def context = start(chosenDefinition(), outerDefinition(ctx -> {
            BeanDefinition<Chosen> definition = ctx.beanContext.getBeanDefinition(Chosen)
            ctx.resolutionContext.getBeanRegistration(definition, Argument.of(Chosen))
        }))

        when:
        Outer outer = context.getBean(Outer)
        Chosen chosen = outer.chosenRegistration.bean

        then: "the chosen bean was created in the resolution the outer bean started"
        chosen.callerAttribute == "outer"
        chosen.rootBeanType == Outer
        chosen.pathBeanTypes.contains(Outer)
        outer.chosenRegistration.beanDefinition.beanType == Chosen

        cleanup:
        context.close()
    }

    void 'test a bean resolved by definition outside a resolution context sees neither'() {
        given:
        def context = start(chosenDefinition())

        when:
        Chosen chosen = context.getBeanRegistration(context.getBeanDefinition(Chosen), Argument.of(Chosen)).bean

        then:
        chosen.callerAttribute == null
        chosen.rootBeanType == Chosen
        !chosen.pathBeanTypes.contains(Outer)

        cleanup:
        context.close()
    }

    void 'test a dependent resolved by definition in the caller resolution context is owned by the caller registration'() {
        given:
        def context = start(chosenDefinition(), outerDefinition(ctx -> {
            BeanDefinition<Chosen> definition = ctx.beanContext.getBeanDefinition(Chosen)
            ctx.resolutionContext.getBeanRegistration(definition, Argument.of(Chosen))
        }))

        when:
        BeanRegistration<Outer> outerRegistration = context.getBeanRegistration(Outer, null)
        Chosen chosen = outerRegistration.bean.chosenRegistration.bean

        then:
        !chosen.disposed
        !chosen.dependency.destroyed

        when: "the outer registration is closed"
        outerRegistration.close()

        then: "the chosen bean and what it created go with it"
        chosen.disposed
        chosen.dependency.destroyed

        cleanup:
        context.close()
    }

    void 'test the registration of a bean resolved by definition in the caller resolution context destroys it on its own'() {
        given:
        def context = start(chosenDefinition(), outerDefinition(ctx -> {
            BeanDefinition<Chosen> definition = ctx.beanContext.getBeanDefinition(Chosen)
            ctx.resolutionContext.getBeanRegistration(definition, Argument.of(Chosen))
        }))

        when:
        BeanRegistration<Outer> outerRegistration = context.getBeanRegistration(Outer, null)
        BeanRegistration<Chosen> chosenRegistration = outerRegistration.bean.chosenRegistration
        chosenRegistration.close()

        then: "closing the dependent registration itself destroys it, as closing one a lookup by type returned does"
        chosenRegistration.bean.disposed
        chosenRegistration.bean.dependency.destroyed

        when:
        outerRegistration.close()

        then: "and the outer bean does not destroy it again"
        noExceptionThrown()

        cleanup:
        context.close()
    }

    void 'test getBean by definition in the caller resolution context is the registration lookup without the registration'() {
        given:
        def context = start(chosenDefinition(), outerDefinition(ctx -> {
            BeanDefinition<Chosen> definition = ctx.beanContext.getBeanDefinition(Chosen)
            Chosen resolved = ctx.resolutionContext.getBean(definition, Argument.of(Chosen))
            BeanRegistration.of(ctx.beanContext, null, definition, resolved)
        }))

        when:
        BeanRegistration<Outer> outerRegistration = context.getBeanRegistration(Outer, null)
        Chosen chosen = outerRegistration.bean.chosenRegistration.bean

        then:
        chosen.callerAttribute == "outer"
        chosen.rootBeanType == Outer

        when:
        outerRegistration.close()

        then:
        chosen.disposed
        chosen.dependency.destroyed

        cleanup:
        context.close()
    }

    void 'test a definition that is not a candidate for the type fails in the caller resolution context'() {
        given:
        def context = start(chosenDefinition(), outerDefinition(ctx -> {
            BeanDefinition<Chosen> definition = ctx.beanContext.getBeanDefinition(Chosen)
            ctx.resolutionContext.getBeanRegistration(definition, (Argument) Argument.of(String))
        }))

        when:
        context.getBean(Outer)

        then:
        def e = thrown(Exception)
        causeOf(e, NoSuchBeanException) != null

        cleanup:
        context.close()
    }

    private static Throwable causeOf(Throwable e, Class<? extends Throwable> type) {
        Throwable current = e
        while (current != null) {
            if (type.isInstance(current)) {
                return current
            }
            current = current.cause
        }
        return null
    }

    static class Chosen {
        final Object callerAttribute
        final Class<?> rootBeanType
        final List<Class<?>> pathBeanTypes
        final DisposableDependency dependency
        boolean disposed

        Chosen(Object callerAttribute, Class<?> rootBeanType, List<Class<?>> pathBeanTypes, DisposableDependency dependency) {
            this.callerAttribute = callerAttribute
            this.rootBeanType = rootBeanType
            this.pathBeanTypes = pathBeanTypes
            this.dependency = dependency
        }
    }

    static class Outer {
        final BeanRegistration<Chosen> chosenRegistration

        Outer(BeanRegistration<Chosen> chosenRegistration) {
            this.chosenRegistration = chosenRegistration
        }
    }
}

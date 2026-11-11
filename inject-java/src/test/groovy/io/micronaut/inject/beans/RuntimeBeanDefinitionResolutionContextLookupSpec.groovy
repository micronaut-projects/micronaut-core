package io.micronaut.inject.beans

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.context.BeanRegistration
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
 * sees the caller as the injection point it is created for, and a dependent it creates belongs to the caller's
 * registration.
 */
class RuntimeBeanDefinitionResolutionContextLookupSpec extends Specification {

    private static RuntimeBeanDefinition<Chosen> chosenDefinition() {
        RuntimeBeanDefinition.builder(Chosen, { RuntimeBeanDefinition.CreationContext ctx ->
            new Chosen(
                    ctx.injectionPoint.map { it.declaringBean.beanType }.orElse(null),
                    ctx.getBean(Argument.of(DisposableDependency))
            )
        } as Function<RuntimeBeanDefinition.CreationContext, Chosen>)
                .scope(Prototype)
                .disposer({ BeanContext bc, Chosen chosen -> chosen.disposed = true } as BiConsumer<BeanContext, Chosen>)
                .build()
    }

    private static RuntimeBeanDefinition<Outer> outerDefinition(Function<RuntimeBeanDefinition.CreationContext, BeanRegistration<Chosen>> resolve) {
        RuntimeBeanDefinition.builder(Outer, { RuntimeBeanDefinition.CreationContext ctx ->
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

    void 'test a bean resolved by definition in the caller resolution context is created for the caller'() {
        given:
        def context = start(chosenDefinition(), outerDefinition(ctx -> {
            BeanDefinition<Chosen> definition = ctx.beanContext.getBeanDefinition(Chosen)
            ctx.getBeanRegistration(definition, Argument.of(Chosen))
        }))

        when:
        Outer outer = context.getBean(Outer)
        Chosen chosen = outer.chosenRegistration.bean

        then: "the chosen bean was created in the resolution the outer bean started"
        chosen.declaringBeanType == Outer
        outer.chosenRegistration.beanDefinition.beanType == Chosen

        cleanup:
        context.close()
    }

    void 'test a bean resolved by definition outside a resolution context is created for no injection point'() {
        given:
        def context = start(chosenDefinition())

        when:
        Chosen chosen = context.getBeanRegistration(context.getBeanDefinition(Chosen), Argument.of(Chosen)).bean

        then:
        chosen.declaringBeanType == null

        cleanup:
        context.close()
    }

    void 'test a dependent resolved by definition in the caller resolution context is owned by the caller registration'() {
        given:
        def context = start(chosenDefinition(), outerDefinition(ctx -> {
            BeanDefinition<Chosen> definition = ctx.beanContext.getBeanDefinition(Chosen)
            ctx.getBeanRegistration(definition, Argument.of(Chosen))
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
            ctx.getBeanRegistration(definition, Argument.of(Chosen))
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

    void 'test the bean of a registration resolved by definition in the caller resolution context has the same lifetime'() {
        given:
        def context = start(chosenDefinition(), outerDefinition(ctx -> {
            BeanDefinition<Chosen> definition = ctx.beanContext.getBeanDefinition(Chosen)
            Chosen resolved = ctx.getBeanRegistration(definition, Argument.of(Chosen)).bean
            BeanRegistration.of(ctx.beanContext, null, definition, resolved)
        }))

        when:
        BeanRegistration<Outer> outerRegistration = context.getBeanRegistration(Outer, null)
        Chosen chosen = outerRegistration.bean.chosenRegistration.bean

        then:
        chosen.declaringBeanType == Outer

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
            ctx.getBeanRegistration(definition, (Argument) Argument.of(String))
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
        final Class<?> declaringBeanType
        final DisposableDependency dependency
        boolean disposed

        Chosen(Class<?> declaringBeanType, DisposableDependency dependency) {
            this.declaringBeanType = declaringBeanType
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

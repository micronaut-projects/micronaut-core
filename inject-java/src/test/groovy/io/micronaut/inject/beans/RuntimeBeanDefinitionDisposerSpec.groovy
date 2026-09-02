package io.micronaut.inject.beans

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.annotation.Prototype
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.DisposableBeanDefinition
import spock.lang.Specification

import java.util.function.BiConsumer

class RuntimeBeanDefinitionDisposerSpec extends Specification {

    void 'test a runtime bean definition built without a disposer is not disposable'() {
        given:
        RuntimeBeanDefinition<Foo> definition = RuntimeBeanDefinition.builder(Foo, () -> new Foo())
                .build()

        expect:
        !(definition instanceof DisposableBeanDefinition)
    }

    void 'test a runtime bean definition built with a disposer is disposable'() {
        given:
        RuntimeBeanDefinition<Foo> definition = RuntimeBeanDefinition.builder(Foo, () -> new Foo())
                .disposer((BiConsumer<BeanContext, Foo>) { ctx, bean -> bean.disposed++ })
                .build()

        expect:
        definition instanceof DisposableBeanDefinition
        definition.beanType == Foo
        !definition.singleton
    }

    void 'test the disposer runs once when a dependent bean registration is closed'() {
        given:
        def calls = []
        def context = ApplicationContext.builder()
                .beanDefinitions(RuntimeBeanDefinition.builder(Foo, () -> new Foo())
                        .scope(Prototype)
                        .disposer((BiConsumer<BeanContext, Foo>) { ctx, bean -> calls << [ctx, bean]; bean.disposed++ })
                        .build())
                .build()
                .start()

        when:
        BeanRegistration<Foo> registration = context.getBeanRegistration(Foo, null)
        Foo foo = registration.bean

        then:
        foo.disposed == 0
        calls.isEmpty()

        when:
        registration.close()

        then:
        foo.disposed == 1
        calls.size() == 1
        calls[0][0].is(context)
        calls[0][1].is(foo)

        when: 'the registration is closed a second time'
        registration.close()

        then: 'the disposer does not run again'
        foo.disposed == 1
        calls.size() == 1

        cleanup:
        context.close()
    }

    void 'test the disposer of a singleton runs once when the context is closed'() {
        given:
        def calls = []
        def context = ApplicationContext.builder()
                .beanDefinitions(RuntimeBeanDefinition.builder(Foo, () -> new Foo())
                        .singleton(true)
                        .disposer((BiConsumer<BeanContext, Foo>) { ctx, bean -> calls << [ctx, bean]; bean.disposed++ })
                        .build())
                .build()
                .start()
        Foo foo = context.getBean(Foo)

        when:
        context.close()

        then:
        foo.disposed == 1
        calls.size() == 1
        calls[0][0].is(context)
        calls[0][1].is(foo)
    }

    void 'test the disposer runs when the bean instance is destroyed through the context'() {
        given:
        def calls = []
        def context = ApplicationContext.builder()
                .beanDefinitions(RuntimeBeanDefinition.builder(Foo, () -> new Foo())
                        .singleton(true)
                        .disposer((BiConsumer<BeanContext, Foo>) { ctx, bean -> calls << [ctx, bean]; bean.disposed++ })
                        .build())
                .build()
                .start()
        Foo foo = context.getBean(Foo)

        when:
        context.destroyBean(foo)

        then:
        foo.disposed == 1
        calls.size() == 1
        calls[0][0].is(context)
        calls[0][1].is(foo)

        when: 'the context is closed afterwards'
        context.close()

        then: 'the already destroyed singleton is not disposed again'
        foo.disposed == 1
        calls.size() == 1
    }

    void 'test the disposer receives the bean resolution context factory instance'() {
        given:
        def calls = []
        def context = ApplicationContext.builder()
                .beanDefinitions(RuntimeBeanDefinition.builder(Foo, resolutionContext -> new Foo())
                        .singleton(true)
                        .disposer((BiConsumer<BeanContext, Foo>) { ctx, bean -> calls << bean })
                        .build())
                .build()
                .start()
        Foo foo = context.getBean(Foo)

        when:
        context.close()

        then:
        calls == [foo]
    }

    void 'test a disposing runtime bean definition keeps the rest of the builder configuration'() {
        given:
        RuntimeBeanDefinition<Foo> definition = RuntimeBeanDefinition.builder(Foo, () -> new Foo())
                .named("foo")
                .exposedTypes(IFoo)
                .singleton(true)
                .disposer((BiConsumer<BeanContext, Foo>) { ctx, bean -> bean.disposed++ })
                .build()

        expect:
        definition instanceof DisposableBeanDefinition
        definition.singleton
        definition.exposedTypes == [IFoo] as Set
        definition.declaredQualifier.toString().contains("foo")

        when:
        def foo = new Foo()
        BeanDefinition<Foo> asDefinition = definition
        ((DisposableBeanDefinition<Foo>) asDefinition).dispose(BeanContext.build(), foo)

        then:
        foo.disposed == 1
    }

    static interface IFoo {
    }

    static class Foo implements IFoo {
        int disposed = 0
    }
}

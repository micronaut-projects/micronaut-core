package io.micronaut.context

import io.micronaut.context.exceptions.BeanInstantiationException
import spock.lang.Specification

class RuntimeBeanDefinitionSingletonSpec extends Specification {

    void 'test the builder honours the singleton argument'() {
        given:
        RuntimeBeanDefinition<Foo> definition = RuntimeBeanDefinition.builder(Foo, () -> new Foo())
                .singleton(isSingleton)
                .build()

        expect:
        definition.singleton == isSingleton

        where:
        isSingleton << [true, false]
    }

    void 'test a runtime bean built with singleton(false) yields a new instance per lookup'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(RuntimeBeanDefinition.builder(Foo, () -> new Foo())
                        .singleton(false)
                        .build())
                .build()
                .start()

        when:
        def first = context.getBean(Foo)
        def second = context.getBean(Foo)

        then:
        !first.is(second)

        cleanup:
        context.close()
    }

    void 'test a runtime bean built with singleton(true) yields the same instance per lookup'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(RuntimeBeanDefinition.builder(Foo, () -> new Foo())
                        .singleton(true)
                        .build())
                .build()
                .start()

        when:
        def first = context.getBean(Foo)
        def second = context.getBean(Foo)

        then:
        first.is(second)

        cleanup:
        context.close()
    }

    void 'failed singleton creation locks are released when the context stops'() {
        given:
        def context = new DefaultBeanContext().start()
        int failures = 0

        when:
        7.times { index ->
            def definition = RuntimeBeanDefinition.builder(Foo, { -> throw new IllegalStateException('failed creation') })
                .singleton(true).named('failure-' + index).build()
            context.registerBeanDefinition(definition)
            try {
                context.getBean(definition)
            } catch (BeanInstantiationException expected) {
                failures++
            }
        }

        then:
        failures == 7
        context.@singletonScope.@singletonsInCreationLocks.size() == 7

        when:
        context.stop()

        then:
        context.@singletonScope.@singletonsInCreationLocks.isEmpty()

        cleanup:
        context.close()
    }

    static class Foo {
    }
}

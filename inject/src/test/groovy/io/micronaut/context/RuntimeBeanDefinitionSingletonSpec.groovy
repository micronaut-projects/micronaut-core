package io.micronaut.context

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

    static class Foo {
    }
}

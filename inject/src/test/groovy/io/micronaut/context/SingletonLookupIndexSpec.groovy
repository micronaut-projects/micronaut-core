package io.micronaut.context

import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class SingletonLookupIndexSpec extends Specification {

    void 'a looked up runtime singleton is found again without a full lookup'() {
        given:
        def context = new DefaultBeanContext().start()
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(Foo, { -> new Foo() }).singleton(true).named('io').build())

        when:
        def foo = context.getBean(Foo, Qualifiers.byName('io'))
        def key = new DefaultBeanContext.BeanKey(Argument.of(Foo), Qualifiers.byName('io'))
        def epoch = context.@beanDefinitionsEpoch.get()

        then:
        context.@singletonScope.findLookedUpSingleton(key, epoch).bean.is(foo)
        context.getBean(Foo, Qualifiers.byName('io')).is(foo)
        context.containsBean(Foo, Qualifiers.byName('io'))

        cleanup:
        context.close()
    }

    void 'a qualified lookup of an any factory singleton finds the delegate made for the qualifier'() {
        given:
        def context = new DefaultBeanContext().start()
        def definition = RuntimeBeanDefinition.builder(Foo, { -> new Foo() }).singleton(true).qualifier(Qualifiers.any()).build()
        context.registerBeanDefinition(definition)

        when:
        def abc = context.getBean(Foo, Qualifiers.byName('ABC'))
        def found = context.findBeanDefinition(Foo, Qualifiers.byName('ABC')).get()

        then:
        found instanceof BeanDefinitionDelegate
        found.getDeclaredQualifier() == Qualifiers.byName('ABC')
        context.getBean(Foo, Qualifiers.byName('ABC')).is(abc)
        !context.getBean(Foo, Qualifiers.byName('XYZ')).is(abc)

        cleanup:
        context.close()
    }

    void 'explicit registrations are recorded when they are registered'() {
        given:
        def context = new DefaultBeanContext().start()
        def created = RuntimeBeanDefinition.builder(Foo, { -> new Foo() }).singleton(true).named('created').build()
        context.registerBeanDefinition(created)
        def first = new Foo()
        def second = new Foo()

        when:
        def createdRegistration = context.@singletonScope.findBeanRegistration(context.getBean(created))
        context.registerSingleton(Foo, first, null, false)
        def firstRegistration = context.@singletonScope.findBeanRegistration(first)
        context.registerSingleton(Foo, second, null, false)
        def secondRegistration = context.@singletonScope.findBeanRegistration(second)

        then:
        !context.@singletonScope.isExplicitRegistration(createdRegistration)
        context.@singletonScope.isExplicitRegistration(firstRegistration)
        context.@singletonScope.isExplicitRegistration(secondRegistration)

        when:
        context.destroyBean(secondRegistration)

        then:
        !context.@singletonScope.isExplicitRegistration(secondRegistration)

        cleanup:
        context.close()
    }

    static class Foo {
    }
}

package io.micronaut.inject.typed

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class BeanTypedSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void 'test limit exposed type'() {
        when:"Lookup a type not exposed"
        context.getBean(Foo2)

        then:"Not there"
        thrown(NoSuchBeanException)

        when:"Lookup a type exposed"
        def bean = context.getBean(Foo1)

        then:"Works"
        bean != null

        when:"Lookup a type not exposed again to trigger looking into existing singletons"
        context.getBean(Foo2)

        then:
        thrown(NoSuchBeanException)
        context.containsBean(Foo1)
        !context.containsBean(Foo2)
        !context.containsBean(FooImpl)
        context.getBeansOfType(Foo1).size() == 1
        context.getBeansOfType(Foo2).size() == 0
        context.getBeansOfType(FooImpl).size() == 0
        context.findBeanDefinition(Foo1).isPresent()
        !context.findBeanDefinition(Foo2).isPresent()
        !context.findBeanDefinition(FooImpl).isPresent()
        context.findBeanDefinition(Foo1).isPresent()
        !context.findBeanDefinition(Foo2).isPresent()
        context.findBeanDefinition(Foo1).isPresent()
        !context.findBeanDefinition(Foo2).isPresent()
        !context.findBeanDefinition(FooImpl).isPresent()
    }
}

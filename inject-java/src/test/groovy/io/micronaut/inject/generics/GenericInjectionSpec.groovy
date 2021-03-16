package io.micronaut.inject.generics

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.stream.Collectors

class GenericInjectionSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void "test narrow injection by generic type"() {
        when:
        def beanType = Argument.of(Engine, V8)
        def bean = context.getBean(Vehicle)


        then:
        bean.start() == 'Starting V8'
        bean.v6Engines.size() == 1
        bean.v6Engines.first().start() == 'Starting V6'
        bean.anotherV8.start() == 'Starting V8'
        bean.anotherV8.is(bean.engine)
        context.getBeanDefinition(beanType)
        context.containsBean(beanType)
        context.streamOfType(beanType).collect(Collectors.toList()).size() == 1
        context.getBeansOfType(beanType).size() == 1

        when:
        context.destroyBean(beanType)
        def another = context.getBean(beanType)

        then:
        !bean.is(another)

    }
}

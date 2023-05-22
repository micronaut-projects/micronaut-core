package io.micronaut.inject.records

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.core.reflect.ClassUtils
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ValidatedBeanDefinition
import io.micronaut.inject.validation.BeanDefinitionValidator
import io.micronaut.validation.validator.Validator

class RecordBeansSpec extends AbstractTypeElementSpec {

    void 'test configuration properties as record'() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name': getClass().getSimpleName()])
        BeanDefinition<?> definition = context.getBeanDefinition(Test)
        when:
        context.registerSingleton(BeanDefinitionValidator, Validator.getInstance())
        context.environment.addPropertySource(PropertySource.of("test",  ['foo.num': 10, 'foo.name':'test']))
        context.getBean(Test)

        then:
        definition instanceof ValidatedBeanDefinition
        ClassUtils.isPresent('io.micronaut.inject.records.$Test$Introspection', context.getClassLoader())
        ClassUtils.isPresent('io.micronaut.inject.records.$Test$Definition', context.getClassLoader())
        !ClassUtils.isPresent('io.micronaut.inject.records.$Test$Definition$Intercepted', context.getClassLoader())

        and:
        def e = thrown(DependencyInjectionException)
        e.cause.message.contains('must be greater than or equal to 20')

        when:
        context.environment.addPropertySource(PropertySource.of("test",  ['foo.num': 25, 'foo.name':'test']))
        def bean = context.getBean(Test)

        then:
        definition.constructor.arguments.length == 4
        bean.num() == 25
        bean.conversionService() != null
        bean.beanContext().is(context)

        cleanup:
        context.close()
    }

    void 'test record bean with nullable annotations'() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name': getClass().getSimpleName()])

        when:
        BeanDefinition<?> definition = context.getBeanDefinition(Test2)

        then:
        !(definition instanceof ValidatedBeanDefinition)
        !ClassUtils.isPresent('io.micronaut.inject.records.$Test2$Introspection', context.getClassLoader())
        ClassUtils.isPresent('io.micronaut.inject.records.$Test2$Definition', context.getClassLoader())
        !ClassUtils.isPresent('io.micronaut.inject.records.$Test2$Definition$Intercepted', context.getClassLoader())

        cleanup:
        context.close()
    }

    void 'test bean that is a record'() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('test.Test', '''
package test;

@jakarta.inject.Singleton
record Test(OtherBean otherBean) {

}

@jakarta.inject.Singleton
class OtherBean {

}
''')

        expect:
        definition.constructor.arguments.length == 1
    }

    void 'test bean factory that returns a record'() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('test.TestFactory', '''
package test;

@io.micronaut.context.annotation.Factory
class TestFactory {
    @io.micronaut.context.annotation.Bean
    Test test(OtherBean otherBean) {
        return new Test(otherBean);
    }
}
record Test(OtherBean otherBean) {

}

@jakarta.inject.Singleton
class OtherBean {

}
''')

        expect:
        definition.constructor.arguments.length == 0
    }
}

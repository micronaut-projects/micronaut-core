package io.micronaut.inject.records

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.validation.BeanDefinitionValidator
import io.micronaut.validation.validator.DefaultValidator
import io.micronaut.validation.validator.Validator
import spock.lang.IgnoreIf

@IgnoreIf({ !jvm.isJava14Compatible() })
class RecordBeansSpec extends AbstractTypeElementSpec {

    void 'test configuration properties as record'() {
        given:
        ApplicationContext context = buildContext('test.Test', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.convert.ConversionService;
import javax.validation.constraints.Min;
import jakarta.inject.Inject;
import io.micronaut.context.BeanContext;

@ConfigurationProperties("foo")
record Test(
    @Min(20) int num, 
    String name, 
    @Primary ConversionService conversionService,
    @Inject BeanContext beanContext) {
}
''')
        def type = context.classLoader.loadClass('test.Test')
        BeanDefinition<?> definition = context.getBeanDefinition(
                type
        )
        when:
        context.registerSingleton(BeanDefinitionValidator, Validator.getInstance())
        context.environment.addPropertySource(PropertySource.of("test",  ['foo.num': 10, 'foo.name':'test']))
        context.getBean(type)

        then:
        def e = thrown(DependencyInjectionException)
        e.cause.message.contains('must be greater than or equal to 20')

        when:
        context.environment.addPropertySource(PropertySource.of("test",  ['foo.num': 25, 'foo.name':'test']))
        def bean = context.getBean(type)

        then:
        definition.constructor.arguments.length == 4
        bean.num() == 25
        bean.conversionService() != null
        bean.beanContext().is(context)

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

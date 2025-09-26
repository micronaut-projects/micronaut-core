package io.micronaut.aop.factory.mapped

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Interceptor
import io.micronaut.context.ApplicationContext
import io.micronaut.context.RuntimeBeanDefinition

class FactoryMappedAdviceSpec extends AbstractTypeElementSpec {

    void "test configuration mapping"() {
        given:
        ApplicationContext applicationContext = buildContext('test.MyConfiguration', '''
package test;


@io.micronaut.aop.factory.mapped.TestConfiguration
public class MyConfiguration {

    @io.micronaut.context.annotation.Bean
    public MyBean myBean() {
        return new MyBean("default");
    }

}

class MyBean {
        private final String name;

        MyBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
''')

        applicationContext.registerBeanDefinition(
                RuntimeBeanDefinition.builder(new TestSingletonInterceptor())
                    .singleton(true)
                    .exposedTypes(Interceptor.class, TestSingletonInterceptor.class)
                    .build()
        )
        def type = applicationContext.classLoader.loadClass('test.MyBean')
        def config = applicationContext.classLoader.loadClass('test.MyConfiguration')

        expect:
        applicationContext.getBean(type) == applicationContext.getBean(type)
        applicationContext.getBean(config).myBean() == applicationContext.getBean(config).myBean()
    }
}

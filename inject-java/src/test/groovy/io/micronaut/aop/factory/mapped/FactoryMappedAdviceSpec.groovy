package io.micronaut.aop.factory.mapped

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.AbstractTypeElementSpec

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

        applicationContext.registerSingleton(new TestSingletonInterceptor())
        def type = applicationContext.classLoader.loadClass('test.MyBean')
        def config = applicationContext.classLoader.loadClass('test.MyConfiguration')

        expect:
        applicationContext.getBean(type) == applicationContext.getBean(type)
        applicationContext.getBean(config).myBean() == applicationContext.getBean(config).myBean()
    }
}

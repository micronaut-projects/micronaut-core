package io.micronaut.spring.beans

import io.micronaut.context.annotation.Prototype
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

import javax.inject.Singleton

@ContextConfiguration(classes = [ByAnnotationTypeConfig])
class MicronautBeanProcessorByAnnotationTypeSpec extends Specification {

    @Autowired
    ApplicationContext applicationContext

    void 'test singleton bean'() {
        expect:
        applicationContext.getBean(SomeSingleton).is applicationContext.getBean(SomeSingleton)
    }

    void 'test prototype beans'() {
        expect:
        !applicationContext.getBean(SomePrototype).is(applicationContext.getBean(SomePrototype))
    }
}

// tag::springconfig[]
@Configuration
class ByAnnotationTypeConfig {

    @Bean
    MicronautBeanProcessor beanProcessor() {
        new MicronautBeanProcessor(Prototype, Singleton)
    }
}
// end::springconfig[]

@Singleton
class SomeSingleton {}

@Prototype
class SomePrototype {}

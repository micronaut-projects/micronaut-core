package io.micronaut.spring.beans

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

import javax.inject.Singleton

@ContextConfiguration(classes = [Config])
class MicronautBeanProcessorSpec extends Specification {

    @Autowired
    ApplicationContext applicationContext

    void 'test singleton bean'() {
        expect:
        applicationContext.getBean(SomeSingleton).is applicationContext.getBean(SomeSingleton)
    }
}

@Configuration
class Config {

    @Bean
    MicronautBeanProcessor singletonProcessor() {
        new MicronautBeanProcessor(Singleton)
    }
}

@Singleton
class SomeSingleton {}

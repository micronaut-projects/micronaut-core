package io.micronaut.spring.beans

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

import javax.inject.Singleton

@ContextConfiguration(classes = [ByConcreteTypeConfig])
class MicronautBeanProcessorByConcreteTypeSpec extends Specification {

    @Autowired
    ApplicationContext applicationContext

    void 'test widget bean'() {
        expect:
        applicationContext.getBean(Widget) instanceof Widget
    }
}

@Configuration
class ByConcreteTypeConfig {

    @Bean
    MicronautBeanProcessor widgetProcessor() {
        new MicronautBeanProcessor(Widget)
    }
}

@Singleton
class Widget {}

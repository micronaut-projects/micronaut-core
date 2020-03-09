package io.micronaut.inject.generics

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.inject.BeanDefinition
import spock.lang.Issue

class FactoryTypeArgumentSpec extends AbstractTypeElementSpec {

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/2829')
    void 'test type argument on factory bean that implements interface'() {
        given:
        ApplicationContext applicationContext = buildContext('test.Test$MyListener0','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.event.StartupEvent;
@Factory
class Test {

    @Bean
    @Replaces(MyListener.class)
    @io.micronaut.runtime.context.scope.Refreshable
    MyListener myListener() {
        return new MyListener();
    }
}

@javax.inject.Singleton
class MyListener implements io.micronaut.context.event.ApplicationEventListener<StartupEvent> {
    public void onApplicationEvent(StartupEvent event) {}

}
''')
        def definition = applicationContext.getBeanDefinition(applicationContext.classLoader.loadClass('test.MyListener'))
        expect:
        definition.getTypeParameters(ApplicationEventListener).size() == 1
        definition.getTypeParameters(ApplicationEventListener)[0] == StartupEvent

        cleanup:
        applicationContext.close()
    }

}

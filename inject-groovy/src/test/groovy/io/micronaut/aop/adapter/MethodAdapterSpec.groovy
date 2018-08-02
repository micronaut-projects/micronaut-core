package io.micronaut.aop.adapter

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.inject.BeanDefinition

class MethodAdapterSpec extends AbstractBeanDefinitionSpec {

    void  "test method adapter produces additional bean"() {
        when:"An adapter method is parsed"
        BeanDefinition definition = buildBeanDefinition('test.MethodAdapterTest$ApplicationEventListener$onStartup$Intercepted','''\
package test;

import io.micronaut.aop.*;
import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.event.*;

@javax.inject.Singleton
class MethodAdapterTest {

    @Adapter(ApplicationEventListener.class)
    void onStartup(StartupEvent event) {
        
    }
}

''')
        then:"Then a bean is produced that is valid"
        definition != null
        ApplicationEventListener.isAssignableFrom(definition.getBeanType())
        !definition.getTypeArguments(ApplicationEventListener).isEmpty()
        definition.getTypeArguments(ApplicationEventListener).get(0).type == StartupEvent


    }
}

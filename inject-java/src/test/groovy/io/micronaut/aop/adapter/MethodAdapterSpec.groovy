package io.micronaut.aop.adapter

import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

class MethodAdapterSpec extends AbstractTypeElementSpec {

    void  "test method adapter produces additional bean"() {
        when:"An adapter method is parsed"
        BeanDefinition definition = buildBeanDefinition('test.Test$ApplicationEventListener$onStartup$Intercepted','''\
package test;

import io.micronaut.aop.*;
import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.event.*;

@javax.inject.Singleton
class Test {

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

    void  "test method adapter wrong argument count"() {
        when:"An adapter method is parsed"
        buildBeanDefinition('test.Test$ApplicationEventListener$onStartup$Intercepted','''\
package test;

import io.micronaut.aop.*;
import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.event.*;

@javax.inject.Singleton
class Test {

    @Adapter(ApplicationEventListener.class)
    void onStartup(StartupEvent event, boolean stuff) {
        
    }
}

''')
        then:"Then a bean is produced that is valid"
        def e = thrown(RuntimeException)
        e.message.contains("Cannot adapt method [onStartup(io.micronaut.context.event.StartupEvent,boolean)] to target method [onApplicationEvent(E)]. Argument lengths don't match.")

    }


    void "test adapter is invoked"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        when:
        Test t = ctx.getBean(Test)

        then:
        t.invoked

        cleanup:
        ctx.close()
    }
}

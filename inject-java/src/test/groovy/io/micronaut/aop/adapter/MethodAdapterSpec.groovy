package io.micronaut.aop.adapter

import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.core.reflect.ReflectionUtils
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

    void  "test method adapter honours type restraints - correct path"() {
        when:"An adapter method is parsed"
        BeanDefinition definition = buildBeanDefinition('test.Test$Foo$myMethod$Intercepted','''\
package test;

import io.micronaut.aop.*;
import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.event.*;

@javax.inject.Singleton
class Test {

    @Adapter(Foo.class)
    void myMethod(String blah) {
        
    }
}

interface Foo<T extends CharSequence> extends java.util.function.Consumer<T> {}
''')
        then:"Then a bean is produced that is valid"
        definition != null
        ReflectionUtils.getAllInterfaces(definition.getBeanType()).find { it.name == 'test.Foo'}
        !definition.getTypeArguments("test.Foo").isEmpty()
        definition.getTypeArguments("test.Foo").get(0).type == String

    }

    void  "test method adapter honours type restraints - compilation error"() {
        when:"An adapter method is parsed"
        BeanDefinition definition = buildBeanDefinition('test.Test$Foo$myMethod$Intercepted','''\
package test;

import io.micronaut.aop.*;
import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.event.*;

@javax.inject.Singleton
class Test {

    @Adapter(Foo.class)
    void myMethod(Integer blah) {
        
    }
}

interface Foo<T extends CharSequence> extends java.util.function.Consumer<T> {}
''')
        then:"An error occurs"
        def e = thrown(RuntimeException)
        e.message.contains 'Cannot adapt method [myMethod(java.lang.Integer)] to target method [accept(T)]. Type [java.lang.Integer] is not a subtype of type [java.lang.CharSequence] for argument at position 0'
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

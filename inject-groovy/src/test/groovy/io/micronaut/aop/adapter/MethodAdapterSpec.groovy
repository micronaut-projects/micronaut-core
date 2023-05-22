/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.aop.adapter

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.inject.BeanDefinition

class MethodAdapterSpec extends AbstractBeanDefinitionSpec {
    void 'test method adapter with failing requirements is not present'() {
        given:
        def context = buildContext('''
package issue5640;

import io.micronaut.aop.Adapter;
import java.lang.annotation.*;
import io.micronaut.context.annotation.Requires;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;
import jakarta.inject.Singleton;
import static java.nio.charset.StandardCharsets.US_ASCII;

@Singleton
@Requires(property="not.present")
class AsciiParser {
    @Parse
    public String parseAsAscii(byte[] value) {
        return new String(value, US_ASCII);
    }
}

@Retention(RUNTIME)
@Target(METHOD)
@Adapter(Parser.class)
@interface Parse {}

interface Parser {
    String parse(byte[] value);
}
''')
        def adaptedType = context.classLoader.loadClass('issue5640.Parser')

        expect:
        !context.containsBean(adaptedType)
        context.getBeansOfType(adaptedType).isEmpty()
    }

    void "test method adapter with overloading"() {
        given:
        def context = buildContext('''
package adapteroverloading;

import io.micronaut.context.event.*;
import io.micronaut.scheduling.annotation.Async;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import io.micronaut.runtime.event.annotation.*;

@Singleton
public class Test {
    boolean invoked = false;
    boolean shutdown = false;

    public boolean getInvoked() {
        return invoked;
    }
    public boolean isShutdown() {
        return shutdown;
    }

    @EventListener
    void receive(StartupEvent event) {
        invoked = true;
    }

    @EventListener
    void receive(ShutdownEvent event) {
        shutdown = true;
    }
}

''')

        when:
        def bean = context.getBean(context.classLoader.loadClass('adapteroverloading.Test'))

        then:
        bean.invoked

        when:
        context.close()

        then:
        bean.shutdown
    }

    void  "test method adapter produces additional bean"() {
        when:"An adapter method is parsed"
        BeanDefinition definition = buildBeanDefinition('test.MethodAdapterTest$ApplicationEventListener$onStartup1$Intercepted','''\
package test;

import io.micronaut.aop.*;
import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.event.*;

@jakarta.inject.Singleton
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

    void "test method adapter inherited from an interface produces additional bean"() {
        when:"An adapter method is parsed"
        BeanDefinition definition = buildBeanDefinition('test.MethodAdapterTest$ApplicationEventListener$onStartup1$Intercepted','''\
package test

import io.micronaut.aop.*
import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*
import io.micronaut.context.event.*

@jakarta.inject.Singleton
class MethodAdapterTest implements Contract {

    @Override
    void onStartup(StartupEvent event) {

    }
}

interface Contract {
    @Adapter(ApplicationEventListener)
    void onStartup(StartupEvent event)
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
        BeanDefinition definition = buildBeanDefinition('test.MethodAdapterTest2$Foo$myMethod1$Intercepted','''\
package test;

import io.micronaut.aop.*;
import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.event.*;

@jakarta.inject.Singleton
class MethodAdapterTest2 {

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
        BeanDefinition definition = buildBeanDefinition('test.MethodAdapterTest3$Foo$myMethod$Intercepted','''\
package test;

import io.micronaut.aop.*;
import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.event.*;

@jakarta.inject.Singleton
class MethodAdapterTest3 {

    @Adapter(Foo.class)
    void myMethod(Integer blah) {

    }
}

interface Foo<T extends CharSequence> extends java.util.function.Consumer<T> {}
''')
        then:"An error occurs"
        def e = thrown(RuntimeException)
        e.message.contains('Cannot adapt method [test.MethodAdapterTest3.myMethod(..)] to target method [java.util.function.Consumer.accept(..)]. Type [java.lang.Integer] is not a subtype of type [java.lang.CharSequence] for argument at position 0')
    }

    void  "test method adapter wrong argument count"() {
        when:"An adapter method is parsed"
        buildBeanDefinition('test.MethodAdapterTest4$ApplicationEventListener$onStartup$Intercepted','''\
package test;

import io.micronaut.aop.*;
import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.event.*;

@jakarta.inject.Singleton
class MethodAdapterTest4 {

    @Adapter(ApplicationEventListener.class)
    void onStartup(StartupEvent event, boolean stuff) {

    }
}

''')
        then:"Then a bean is produced that is valid"
        def e = thrown(RuntimeException)
        e.message.contains "Cannot adapt method [test.MethodAdapterTest4.onStartup(..)] to target method [io.micronaut.context.event.ApplicationEventListener.onApplicationEvent(..)]. Argument lengths don't match."

    }
}

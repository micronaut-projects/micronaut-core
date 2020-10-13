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
package io.micronaut.aop.introduction

import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.inject.writer.BeanDefinitionVisitor

/**
 * @author graemerocher
 * @since 1.0
 */
class IntroductionAdviceWithNewInterfaceSpec extends AbstractTypeElementSpec {

    void "test that it is possible for @Introduction advice to implement additional interfaces on concrete classes"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;

@ListenerAdvice
@Stub
@javax.inject.Singleton
class MyBean  {

    @Executable
    public String getFoo() { return "good"; }
}

''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        ApplicationEventListener.class.isAssignableFrom(beanDefinition.beanType)
        beanDefinition.injectedFields.size() == 0
        beanDefinition.executableMethods.size() == 2
        beanDefinition.findMethod("getFoo").isPresent()
        beanDefinition.findMethod("onApplicationEvent", Object).isPresent()

        when:
        def context = new DefaultBeanContext()
        context.start()
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)
        ListenerAdviceInterceptor listenerAdviceInterceptor= context.getBean(ListenerAdviceInterceptor)
        listenerAdviceInterceptor.recievedMessages.clear()
        then:"the methods are invocable"
        listenerAdviceInterceptor.recievedMessages.isEmpty()
        instance.getFoo() == "good"
        instance.onApplicationEvent(new Object()) == null
        !listenerAdviceInterceptor.recievedMessages.isEmpty()

    }

    void "test that it is possible for @Introduction advice to implement additional interfaces on abstract classes"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;

@ListenerAdvice
@Stub
@javax.inject.Singleton
abstract class MyBean  {

    @Executable
    public String getFoo() { return "good"; }
}

''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        ApplicationEventListener.class.isAssignableFrom(beanDefinition.beanType)
        beanDefinition.injectedFields.size() == 0
        beanDefinition.executableMethods.size() == 2
        beanDefinition.findMethod("getFoo").isPresent()
        beanDefinition.findMethod("onApplicationEvent", Object).isPresent()

        when:
        def context = new DefaultBeanContext()
        context.start()
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)
        ListenerAdviceInterceptor listenerAdviceInterceptor= context.getBean(ListenerAdviceInterceptor)
        listenerAdviceInterceptor.recievedMessages.clear()
        then:"the methods are invocable"
        listenerAdviceInterceptor.recievedMessages.isEmpty()
        instance.getFoo() == "good"
        instance.onApplicationEvent(new Object()) == null
        !listenerAdviceInterceptor.recievedMessages.isEmpty()

    }



    void "test that it is possible for @Introduction advice to implement additional interfaces on interfaces"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;

@ListenerAdvice
@Stub
@javax.inject.Singleton
interface MyBean  {

    @Executable
    String getBar(); 
    
    @Executable
    default String getFoo() { return "good"; }
}

''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        ApplicationEventListener.class.isAssignableFrom(beanDefinition.beanType)
        beanDefinition.injectedFields.size() == 0
        beanDefinition.executableMethods.size() == 2
        beanDefinition.findMethod("getBar").isPresent()
        beanDefinition.findMethod("onApplicationEvent", Object).isPresent()

        when:
        def context = new DefaultBeanContext()
        context.start()
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)
        ListenerAdviceInterceptor listenerAdviceInterceptor= context.getBean(ListenerAdviceInterceptor)
        listenerAdviceInterceptor.recievedMessages.clear()

        then:"the methods are invocable"
        listenerAdviceInterceptor.recievedMessages.isEmpty()
        instance.getFoo() == "good"
        instance.getBar() == null
        instance.onApplicationEvent(new Object()) == null
        !listenerAdviceInterceptor.recievedMessages.isEmpty()
        listenerAdviceInterceptor.recievedMessages.size() == 1

    }

    void "test an interface with non overriding but subclass return type method"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;

@Stub
@javax.inject.Singleton
interface MyBean extends GenericInterface, SpecificInterface {

}

class Generic {
}
class Specific extends Generic {
}
interface GenericInterface {   
    Generic getObject();
}
interface SpecificInterface {    
    Specific getObject();
}
''')

        then:
        noExceptionThrown()
        beanDefinition != null

        when:
        def context = new DefaultBeanContext()
        context.start()
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)

        then:
        //I ended up going this route because actually calling the methods here would be relying on
        //having the target interface in the bytecode of the test
        instance.$proxyMethods.length == 2
    }
}

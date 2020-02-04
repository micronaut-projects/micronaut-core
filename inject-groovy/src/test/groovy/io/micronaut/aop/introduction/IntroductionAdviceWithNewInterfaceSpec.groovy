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

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.inject.writer.BeanDefinitionVisitor

/**
 * @author graemerocher
 * @since 1.0
 */
class IntroductionAdviceWithNewInterfaceSpec extends AbstractBeanDefinitionSpec {

    void "test that it is possible for @Introduction advice to implement additional interfaces on concrete classes"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('io.micronaut.aop.introduction.test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package io.micronaut.aop.introduction.test;

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
        beanDefinition.injectedFields.size() == 0
        beanDefinition.executableMethods.size() == 2
        beanDefinition.findMethod("onApplicationEvent", Object).isPresent()
        ApplicationEventListener.class.isAssignableFrom(beanDefinition.beanType)

        when:
        def context = new DefaultBeanContext()
        context.start()
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)
        ListenerAdviceInterceptor listenerAdviceInterceptor= context.getBean(ListenerAdviceInterceptor)

        then:"the methods are invocable"
        listenerAdviceInterceptor.recievedMessages.isEmpty()
        instance.getFoo() == "good"
        instance.onApplicationEvent(new Object()) == null
        !listenerAdviceInterceptor.recievedMessages.isEmpty()

    }

    void "test that it is possible for @Introduction advice to implement additional interfaces on abstract classes"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('io.micronaut.aop.introduction.test.MyBean2' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package io.micronaut.aop.introduction.test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;

@ListenerAdvice
@Stub
@javax.inject.Singleton
abstract class MyBean2 {

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
        beanDefinition.findMethod("onApplicationEvent", Object).isPresent()

        when:
        def context = new DefaultBeanContext()
        context.start()
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)
        ListenerAdviceInterceptor listenerAdviceInterceptor= context.getBean(ListenerAdviceInterceptor)

        then:"the methods are invocable"
        listenerAdviceInterceptor.recievedMessages.isEmpty()
        instance.getFoo() == "good"
        instance.onApplicationEvent(new Object()) == null
        !listenerAdviceInterceptor.recievedMessages.isEmpty()

    }



    void "test that it is possible for @Introduction advice to implement additional interfaces on interfaces"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('io.micronaut.aop.introduction.test.MyBean3' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package io.micronaut.aop.introduction.test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;

@ListenerAdvice
@Stub
@javax.inject.Singleton
interface MyBean3  {

    @Executable
    String getBar(); 
    
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

        then:"the methods are invocable"
        listenerAdviceInterceptor.recievedMessages.isEmpty()
        instance.getBar() == null
        instance.onApplicationEvent(new Object()) == null
        !listenerAdviceInterceptor.recievedMessages.isEmpty()
        listenerAdviceInterceptor.recievedMessages.size() == 1

    }
}

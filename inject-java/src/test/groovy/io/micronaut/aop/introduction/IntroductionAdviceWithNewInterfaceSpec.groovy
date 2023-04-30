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

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
/**
 * @author graemerocher
 * @since 1.0
 */
class IntroductionAdviceWithNewInterfaceSpec extends AbstractTypeElementSpec {


    void "test introduction advice with primitive generics"() {
        when:
        def context = buildContext( 'test.MyRepo', '''
package test;

import io.micronaut.aop.introduction.*;
import jakarta.validation.constraints.NotNull;

@RepoDef
interface MyRepo extends DeleteByIdCrudRepo<Integer> {

    @Override void deleteById(@NotNull Integer integer);
}


''', true)

        def bean =
                getBean(context, 'test.MyRepo')
        then:
        bean != null
    }

    void "test that it is possible for @Introduction advice to implement additional interfaces on concrete classes"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;

@ListenerAdvice
@Stub
@jakarta.inject.Singleton
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
        def instance = ((InstantiatableBeanDefinition)beanDefinition).instantiate(context)
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
@jakarta.inject.Singleton
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
        def instance = ((InstantiatableBeanDefinition)beanDefinition).instantiate(context)
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
@jakarta.inject.Singleton
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
        def context = BeanContext.run()
        def instance = ((InstantiatableBeanDefinition)beanDefinition).instantiate(context)
        ListenerAdviceInterceptor listenerAdviceInterceptor= context.getBean(ListenerAdviceInterceptor)
        listenerAdviceInterceptor.recievedMessages.clear()

        then:"the methods are invocable"
        listenerAdviceInterceptor.recievedMessages.isEmpty()
        instance.getFoo() == "good"
        instance.getBar() == null
        instance.onApplicationEvent(new Object()) == null
        !listenerAdviceInterceptor.recievedMessages.isEmpty()
        listenerAdviceInterceptor.recievedMessages.size() == 1

        cleanup:
        context.close()
    }

    void "test an interface with non overriding but subclass return type method"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;

@Stub
@jakarta.inject.Singleton
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
        def instance = ((InstantiatableBeanDefinition)beanDefinition).instantiate(context)

        then:
        //I ended up going this route because actually calling the methods here would be relying on
        //having the target interface in the bytecode of the test
        instance.$proxyMethods.length == 2
    }

    void "test interface multiple inheritance"() {
        when:
            BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterfaceX' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.annotation.Executable;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Stub
@jakarta.inject.Singleton
interface MyInterfaceX extends MyInterface2 {

    @MyAnn
    String myMethod5(String param);

    default String myMethod6(String param) {
        return myMethod3(param);
    }
}

interface MyInterface2 extends MyInterface3, MyInterface4 {

    @MyAnn
    String myMethod1(String param);

    @MyAnn
    @Override
    String myMethod3(String param);

    @Override
    String myMethod2(String param);

    @MyAnn
    @Override
    String myMethod4(String param);

    default String myMethod7(String param) {
        return myMethod4(param);
    }
}

interface MyInterface3 {
    @MyAnn
    String myMethod2(String param);
}

interface MyInterface4 {
    String myMethod3(String param);

    String myMethod4(String param);
}

@Documented
@Retention(RUNTIME)
@Executable
@interface MyAnn {
}
''')

        then:
            noExceptionThrown()
            beanDefinition != null

        when:
            def context = new DefaultBeanContext()
            context.start()
            def instance = ((InstantiatableBeanDefinition)beanDefinition).instantiate(context)
            def introducer = context.getBean(StubIntroducer)
        then:
            instance.myMethod1("abc1") == "abc1"
            introducer.visitedMethods["myMethod1"].hasAnnotation("test.MyAnn")
            instance.myMethod2("abc2") == "abc2"
            !introducer.visitedMethods["myMethod2"].hasAnnotation("test.MyAnn")
            instance.myMethod3("abc3") == "abc3"
            introducer.visitedMethods["myMethod3"].hasAnnotation("test.MyAnn")
            instance.myMethod4("abc4") == "abc4"
            introducer.visitedMethods["myMethod4"].hasAnnotation("test.MyAnn")
            instance.myMethod5("abc5") == "abc5"
            introducer.visitedMethods["myMethod5"].hasAnnotation("test.MyAnn")
            instance.myMethod6("abc6") == "abc6" // Calls method3
            introducer.visitedMethods["myMethod3"].hasAnnotation("test.MyAnn")
            instance.myMethod7("abc7") == "abc7" // Calls method4
            introducer.visitedMethods["myMethod4"].hasAnnotation("test.MyAnn")

        cleanup:
            context.close()
    }
}

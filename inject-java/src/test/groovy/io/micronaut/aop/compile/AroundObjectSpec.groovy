/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.aop.compile

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Blocking
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.inject.writer.BeanDefinitionVisitor

/**
 * @author Jaroslav Tulach
 * @since 2.2
 */
class AroundObjectSpec extends AbstractTypeElementSpec {

    void "test that @Around can intercept also Object methods"() {
        when:
        ApplicationContext ctx = buildContext('test.MyBean', '''
package test;

import io.micronaut.aop.*;
import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import java.lang.annotation.*;\n\
import java.util.Properties;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
@Around(wrapObjectMethods = true)
@Type(EqualsInterceptor.class)
@interface EverythingEvenEqualsHashCodeToString{}

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
@Around
@Type(EqualsInterceptor.class)
@interface NoObjectMethods{}


@javax.inject.Singleton
class EqualsInterceptor implements MethodInterceptor<Object, Object> {\n\
    public final Properties methods = new Properties();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {\n\
        methods.setProperty(context.getMethodName(), "true");
        return context.proceed();
    }
}


@EverythingEvenEqualsHashCodeToString
class MyBean {\n\
    public String someMethod() {\n\
        return "MyBean";\n\
    }
}

@NoObjectMethods
class ClassicalBean {\n\
    public String someMethod() {\n\
        return "ClassicalBean";\n\
    }
}

''')
        then:
        def classicalBean = ctx.getBean(ctx.classLoader.loadClass("test.ClassicalBean"))
        def interceptor = ctx.getBean(ctx.classLoader.loadClass("test.EqualsInterceptor"))
        interceptor.methods.getProperty("someMethod") == null
        classicalBean.someMethod()
        interceptor.methods.getProperty("someMethod") != null

        interceptor.methods.getProperty("equals") == null
        classicalBean.equals(classicalBean)
        interceptor.methods.getProperty("equals") == null

        interceptor.methods.getProperty("hashCode") == null
        classicalBean.hashCode()
        interceptor.methods.getProperty("hashCode") == null

        interceptor.methods.getProperty("toString") == null
        classicalBean.toString()
        interceptor.methods.getProperty("toString") == null

        def myBean = ctx.getBean(ctx.classLoader.loadClass("test.MyBean"))
        interceptor.methods.remove("someMethod")
        myBean.someMethod()
        interceptor.methods.getProperty("someMethod") != null

        interceptor.methods.getProperty("equals") == null
        myBean.equals(myBean)
        interceptor.methods.getProperty("equals") != null

        interceptor.methods.getProperty("hashCode") == null
        myBean.hashCode()
        interceptor.methods.getProperty("hashCode") != null

        interceptor.methods.getProperty("toString") == null
        myBean.toString()
        interceptor.methods.getProperty("toString") != null
    }
}

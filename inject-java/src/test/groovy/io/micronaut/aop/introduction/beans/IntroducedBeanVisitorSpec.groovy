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
package io.micronaut.aop.introduction.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import org.reactivestreams.Publisher

class IntroducedBeanVisitorSpec extends AbstractTypeElementSpec {

    void "test introduced bean visitor"() {
        given:
            def context = buildContext("""
package introducedbeanspec;

import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;
import java.lang.annotation.*;
import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import java.util.Optional;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@Inherited
@interface XMyDataMethod {
}

@Introduction
@Type(MyRepoIntroducer.class)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
@interface RepoDef {
}


@Singleton
class MyRepoIntroducer implements MethodInterceptor<Object, Object> {

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return null;
    }
}

interface Repo1 {
    Publisher<MyBean> findAll();

    Publisher<MyBean> method1();
}

interface Repo2<E> {
    Publisher<E> findAll();

    Publisher<E> method2();
}

@RepoDef
interface Repo3 extends Repo2<MyBean>, Repo1 {

    Publisher<MyBean> method3();

}

class MyBean {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}


""")

        when:
            def beanDef1 = context.getBeanDefinition(context.classLoader.loadClass("introducedbeanspec.Repo3"))
            def findAllMethod = beanDef1.getRequiredMethod("findAll")
            def method1 = beanDef1.getRequiredMethod("method1")
            def method2 = beanDef1.getRequiredMethod("method2")
            def method3 = beanDef1.getRequiredMethod("method3")
        then:
            findAllMethod.hasAnnotation("introducedbeanspec.XMyDataMethod")
            method1.hasAnnotation("introducedbeanspec.XMyDataMethod")
            method2.hasAnnotation("introducedbeanspec.XMyDataMethod")
            method3.hasAnnotation("introducedbeanspec.XMyDataMethod")

        cleanup:
            context.close()
    }

    void "test introduced bean visitor 2"() {
        given:
            def context = buildContext("""
package introducedbeanspec2;

import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;
import java.lang.annotation.*;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import java.util.Optional;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@Inherited
@interface XMyDataMethod {
}

@Introduction
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
@interface RepoDef {
}


@Singleton
@InterceptorBean(RepoDef.class)
class MyRepoIntroducer implements MethodInterceptor<Object, Object> {

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return null;
    }
}

interface Repo1 {
    Publisher<MyBean> findAll();

    Publisher<MyBean> method1();
}

interface Repo2<E> {
    Publisher<E> findAll();

    Publisher<E> method2();
}

@RepoDef
interface Repo3 extends Repo2<MyBean>, Repo1 {

    Publisher<MyBean> method3();

}

class MyBean {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}


""")

        when:
            def beanDef1 = context.getBeanDefinition(context.classLoader.loadClass("introducedbeanspec2.Repo3"))
            def findAllMethod = beanDef1.getRequiredMethod("findAll")
            def method1 = beanDef1.getRequiredMethod("method1")
            def method2 = beanDef1.getRequiredMethod("method2")
            def method3 = beanDef1.getRequiredMethod("method3")
        then:
            findAllMethod.hasAnnotation("introducedbeanspec2.XMyDataMethod")
            method1.hasAnnotation("introducedbeanspec2.XMyDataMethod")
            method2.hasAnnotation("introducedbeanspec2.XMyDataMethod")
            method3.hasAnnotation("introducedbeanspec2.XMyDataMethod")

        cleanup:
            context.close()
    }

    void "test introduced bean visitor 3"() {
        given:
            def context = buildContext("""
package introducedbeanspec;

import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;
import java.lang.annotation.*;
import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import java.util.Optional;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@Inherited
@interface XMyDataMethod {
}

@Introduction
@Type(MyRepoIntroducer.class)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
@interface RepoDef {
}


@Singleton
class MyRepoIntroducer implements MethodInterceptor<Object, Object> {

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return null;
    }
}

interface Repo1 {
    void findAll(Publisher<MyBean> publisher);

    void method1(Publisher<MyBean> publisher);
}

interface Repo2<E> {
    void findAll(Publisher<E> e);

    void method2(Publisher<E> e);
}

@RepoDef
interface Repo3 extends Repo2<MyBean>, Repo1 {

    void method3(Publisher<MyBean> p);

}

class MyBean {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}


""")

        when:
            def beanDef1 = context.getBeanDefinition(context.classLoader.loadClass("introducedbeanspec.Repo3"))
            def findAllMethod = beanDef1.getRequiredMethod("findAll", Publisher)
            def method1 = beanDef1.getRequiredMethod("method1", Publisher)
            def method2 = beanDef1.getRequiredMethod("method2", Publisher)
            def method3 = beanDef1.getRequiredMethod("method3", Publisher)
        then:
            findAllMethod.hasAnnotation("introducedbeanspec.XMyDataMethod")
            method1.hasAnnotation("introducedbeanspec.XMyDataMethod")
            method2.hasAnnotation("introducedbeanspec.XMyDataMethod")
            method3.hasAnnotation("introducedbeanspec.XMyDataMethod")

        cleanup:
            context.close()
    }

    void "test introduced bean visitor 4"() {
        given:
            def context = buildContext("""
package introducedbeanspec;

import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;
import java.lang.annotation.*;
import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import java.util.Optional;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@Inherited
@interface XMyDataMethod {
}

@Introduction
@Type(MyRepoIntroducer.class)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
@interface RepoDef {
}


@Singleton
class MyRepoIntroducer implements MethodInterceptor<Object, Object> {

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return null;
    }
}

interface Repo1 {
    void findAll(MyBean b);

    void method1(MyBean b);
}

interface Repo2<E> {
    void findAll(E e);

    void method2(E e);
}

@RepoDef
interface Repo3 extends Repo2<MyBean>, Repo1 {

    void method3(MyBean p);

}

class MyBean {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}


""")

        when:
            def clazz = context.classLoader.loadClass("introducedbeanspec.MyBean")
            def beanDef1 = context.getBeanDefinition(context.classLoader.loadClass("introducedbeanspec.Repo3"))
            def findAllMethod = beanDef1.getRequiredMethod("findAll", clazz)
            def method1 = beanDef1.getRequiredMethod("method1", clazz)
            def method2 = beanDef1.getRequiredMethod("method2", clazz)
            def method3 = beanDef1.getRequiredMethod("method3", clazz)
        then:
            findAllMethod.hasAnnotation("introducedbeanspec.XMyDataMethod")
            method1.hasAnnotation("introducedbeanspec.XMyDataMethod")
            method2.hasAnnotation("introducedbeanspec.XMyDataMethod")
            method3.hasAnnotation("introducedbeanspec.XMyDataMethod")

        cleanup:
            context.close()
    }
}

/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.aop.compile;

import io.micronaut.aop.Intercepted;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.annotation.NamedAnnotationTransformer;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import io.micronaut.core.reflect.ReflectionUtils;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of AroundCompileSpec (Spock) to JUnit 5.
 */
class AroundCompileTest extends AbstractTypeElementTest {

    // Reflection helpers for dynamic beans defined within tests
    private static Boolean getBooleanField(Object obj, String field) {
        Object v = ReflectionUtils.getField(obj.getClass(), field, obj);
        return (v instanceof Boolean b) ? b : Boolean.valueOf(String.valueOf(v));
    }

    private static void setBooleanField(Object obj, String field, boolean value) {
        ReflectionUtils.setField(obj.getClass(), field, obj, value);
    }

    private static int getIntField(Object obj, String field) {
        Object v = ReflectionUtils.getField(obj.getClass(), field, obj);
        return (v instanceof Number n) ? n.intValue() : Integer.parseInt(String.valueOf(v));
    }

    private static long getLongField(Object obj, String field) {
        Object v = ReflectionUtils.getField(obj.getClass(), field, obj);
        return (v instanceof Number n) ? n.longValue() : Long.parseLong(String.valueOf(v));
    }

    // Generic invocation helpers to avoid IllegalAccess/NoSuchMethod on intercepted proxies
    private static Object invoke(Object bean, String name, Object... args) {
        try {
            Class<?>[] argTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                Object a = args[i];
                if (a == null) {
                    argTypes[i] = Object.class;
                } else if (a instanceof Integer) {
                    argTypes[i] = int.class;
                } else if (a instanceof Short) {
                    argTypes[i] = short.class;
                } else if (a instanceof Byte) {
                    argTypes[i] = byte.class;
                } else if (a instanceof Long) {
                    argTypes[i] = long.class;
                } else if (a instanceof Float) {
                    argTypes[i] = float.class;
                } else if (a instanceof Double) {
                    argTypes[i] = double.class;
                } else if (a instanceof Boolean) {
                    argTypes[i] = boolean.class;
                } else if (a instanceof Character) {
                    argTypes[i] = char.class;
                } else if (a instanceof byte[]) {
                    argTypes[i] = byte[].class;
                } else {
                    argTypes[i] = a.getClass();
                }
            }
            try {
                var m = bean.getClass().getMethod(name, argTypes);
                m.setAccessible(true);
                return m.invoke(bean, args);
            } catch (NoSuchMethodException e) {
                Class<?> c = bean.getClass();
                while (c != null) {
                    try {
                        var m = c.getDeclaredMethod(name, argTypes);
                        m.setAccessible(true);
                        return m.invoke(bean, args);
                    } catch (NoSuchMethodException ignore) {
                        c = c.getSuperclass();
                    }
                }
                throw e;
            }
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable cause = ite.getTargetException();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object invokeWithTypes(Object bean, String name, Class<?>[] argTypes, Object... args) {
        try {
            try {
                var m = bean.getClass().getMethod(name, argTypes);
                m.setAccessible(true);
                return m.invoke(bean, args);
            } catch (NoSuchMethodException e) {
                Class<?> c = bean.getClass();
                while (c != null) {
                    try {
                        var m = c.getDeclaredMethod(name, argTypes);
                        m.setAccessible(true);
                        return m.invoke(bean, args);
                    } catch (NoSuchMethodException ignore) {
                        c = c.getSuperclass();
                    }
                }
                throw e;
            }
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable cause = ite.getTargetException();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testAroundOnIntroducedMethod() throws Exception {
        ApplicationContext context = buildContext("""
            package introductiontest;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @TestIntroductionAnn
            interface MyBean {

                @TestAroundAnn
                int test();

            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @InterceptorBinding(kind = InterceptorKind.INTRODUCTION)
            @interface TestIntroductionAnn {
            }

            @InterceptorBean(TestIntroductionAnn.class)
            class StubIntroduction implements Interceptor {
                int invoked = 0;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked++;
                    return 10;
                }
            }

            @Inherited
            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @InterceptorBinding
            @interface TestAroundAnn {
            }

            @InterceptorBean(TestAroundAnn.class)
            class TestInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }
            """);
        try {
            Object instance = getBean(context, "introductiontest.MyBean");
            Object introductionInterceptor = getBean(context, "introductiontest.StubIntroduction");
            Object aroundInterceptor = getBean(context, "introductiontest.TestInterceptor");

            int result = (Integer) invoke(instance, "test");

            org.junit.jupiter.api.Assertions.assertInstanceOf(Intercepted.class, instance);
            assertEquals(10, result);
            assertEquals(1, getIntField(introductionInterceptor, "invoked"));
            assertTrue(getBooleanField(aroundInterceptor, "invoked"));
        } finally {
            context.close();
        }
    }

    @Test
    void testApplyInterceptorUsingInterface() throws Exception {
        ApplicationContext context = buildContext("""
            package test;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            interface IMyBean {
                @TestAnn
                void test();
            }

            @Singleton
            class MyBean implements IMyBean {

                @Override
                public void test() {

                }

            }

            @Inherited
            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @InterceptorBinding
            @interface TestAnn {
            }

            @InterceptorBean(TestAnn.class)
            class TestInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }
            """);
        try {
            Object instance = getBean(context, "test.MyBean");
            Object interceptor = getBean(context, "test.TestInterceptor");

            invoke(instance, "test");

            org.junit.jupiter.api.Assertions.assertInstanceOf(Intercepted.class, instance);
            assertTrue(getBooleanField(interceptor, "invoked"));
        } finally {
            context.close();
        }
    }

    @Test
    void testApplyInterceptorUsingInterfaceReturnEnum() throws Exception {
        ApplicationContext context = buildContext("""
            package test;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            interface IMyBean {
                @TestAnn
                <E extends Enum<E>> E test();
            }

            @Singleton
            class MyBean implements IMyBean {

                @Override
                public <E extends Enum<E>> E test() {
                    return null;
                }

            }

            @Inherited
            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @InterceptorBinding
            @interface TestAnn {
            }

            @InterceptorBean(TestAnn.class)
            class TestInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }
            """);
        try {
            Object instance = getBean(context, "test.MyBean");
            Object interceptor = getBean(context, "test.TestInterceptor");

            Object res = invoke(instance, "test");
            assertNull(res);
            org.junit.jupiter.api.Assertions.assertInstanceOf(Intercepted.class, instance);
            assertTrue(getBooleanField(interceptor, "invoked"));
        } finally {
            context.close();
        }
    }

    @Test
    void testApplyInterceptorUsingInterfaceParamEnum() throws Exception {
        ApplicationContext context = buildContext("""
            package test;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            interface IMyBean {
                @TestAnn
                <E extends Enum<E>> String test(E param);
            }

            @Singleton
            class MyBean implements IMyBean {

                @Override
                public <E extends Enum<E>> String test(E param) {
                    return "sss";
                }

            }

            @Inherited
            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @InterceptorBinding
            @interface TestAnn {
            }

            @InterceptorBean(TestAnn.class)
            class TestInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }
            """);
        try {
            Object instance = getBean(context, "test.MyBean");
            Object interceptor = getBean(context, "test.TestInterceptor");

            String s = (String) invokeWithTypes(instance, "test", new Class[]{Enum.class}, new Object[]{null});
            assertEquals("sss", s);

            assertTrue(instance instanceof Intercepted);
            assertTrue(getBooleanField(interceptor, "invoked"));
        } finally {
            context.close();
        }
    }

    @Test
    void testStereotypeMethodLevelInterceptorMatching() throws Exception {
        ApplicationContext context = buildContext("""
            package annbinding2;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;
            import io.micronaut.aop.simple.*;

            @Singleton
            class MyBean {
                @TestAnn2
                void test() {

                }

            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Around
            @interface TestAnn {
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @TestAnn
            @interface TestAnn2 {
            }

            @InterceptorBean(TestAnn.class)
            class TestInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }
            """);
        try {
            Object instance = getBean(context, "annbinding2.MyBean");
            Object interceptor = getBean(context, "annbinding2.TestInterceptor");

            invoke(instance, "test");

            assertTrue(instance instanceof Intercepted);
            assertTrue(getBooleanField(interceptor, "invoked"));
        } finally {
            context.close();
        }
    }

    @Test
    void testApplyInterceptorBinderWithAnnotationMapper() throws Exception {
        ApplicationContext context = buildContext("""
            package mapperbinding;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @Singleton
            class MyBean {
                @TestAnn
                void test() {

                }

            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Around
            @interface TestAnn {
            }

            @InterceptorBean(TestAnn.class)
            class TestInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }
            """);
        try {
            Object instance = getBean(context, "mapperbinding.MyBean");
            Object interceptor = getBean(context, "mapperbinding.TestInterceptor");

            invoke(instance, "test");

            assertTrue(instance instanceof Intercepted);
            assertTrue(getBooleanField(interceptor, "invoked"));
        } finally {
            context.close();
        }
    }

    @Test
    void testApplyInterceptorBinderWithAnnotationMapperPlusMembers() throws Exception {
        ApplicationContext context = buildContext("""
            package mapperbindingmembers;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import jakarta.inject.Singleton;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @Singleton
            class MyBean {
                @TestAnn(num=1)
                void test() {
                }
            }

            @Retention(RUNTIME)
            @Target({ElementType.ANNOTATION_TYPE})
            @interface MyInterceptorBinding {
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @MyInterceptorBinding
            @interface TestAnn {
                int num();
            }

            @Singleton
            @TestAnn(num=1)
            class TestInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }

            @Singleton
            @TestAnn(num=2)
            class TestInterceptor2 implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }
            """);
        try {
            Object instance = getBean(context, "mapperbindingmembers.MyBean");
            Object interceptor = getBean(context, "mapperbindingmembers.TestInterceptor");
            Object interceptor2 = getBean(context, "mapperbindingmembers.TestInterceptor2");

            invoke(instance, "test");

            assertTrue(instance instanceof Intercepted);
            assertTrue(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(interceptor2, "invoked"));
        } finally {
            context.close();
        }
    }

    @Test
    void testMethodLevelInterceptorMatching() throws Exception {
        ApplicationContext context = buildContext("""
            package annbinding2;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;
            import io.micronaut.aop.simple.*;

            @Singleton
            class MyBean {
                @TestAnn
                void test() {

                }

                @TestAnn2
                void test2() {

                }
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Around
            @interface TestAnn {
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Around
            @interface TestAnn2 {
            }

            @InterceptorBean(TestAnn.class)
            class TestInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }

            @InterceptorBean(TestAnn2.class)
            class AnotherInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }
            """);
        try {
            Object instance = getBean(context, "annbinding2.MyBean");
            Object interceptor = getBean(context, "annbinding2.TestInterceptor");
            Object anotherInterceptor = getBean(context, "annbinding2.AnotherInterceptor");

            invoke(instance, "test");
            assertTrue(instance instanceof Intercepted);
            assertTrue(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(anotherInterceptor, "invoked"));

            invoke(instance, "test2");
            assertTrue(getBooleanField(anotherInterceptor, "invoked"));
        } finally {
            context.close();
        }
    }

    @Test
    void testAnnotationWithJustInterceptorBinding() throws Exception {
        ApplicationContext context = buildContext("""
            package annbinding1;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @Singleton
            @TestAnn
            class MyBean {
                void test() {
                }
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @InterceptorBinding
            @interface TestAnn {
            }

            @Singleton
            @InterceptorBinding(TestAnn.class)
            class TestInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }

            @Singleton
            class AnotherInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }
            """);
        try {
            Object instance = getBean(context, "annbinding1.MyBean");
            Object interceptor = getBean(context, "annbinding1.TestInterceptor");
            Object anotherInterceptor = getBean(context, "annbinding1.AnotherInterceptor");
            invoke(instance, "test");

            assertTrue(instance instanceof Intercepted);
            assertTrue(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(anotherInterceptor, "invoked"));
        } finally {
            context.close();
        }
    }

    @Test
    void testAroundWithInheritanceAndGenerics() throws Exception {
        ApplicationContext context = buildContext("""
            package annbinding1;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @Singleton
            @TestAnn
            class MyBean implements Middle<String> {
                void test() {
                }
            }

            interface Middle<ParentIdT> extends Parent<ParentIdT> {
                @Override
                default String updateResource(String request, ParentIdT parentId) {
                    return Parent.super.updateResource(request,parentId);
                }
            }
            interface Parent<ParentIdT> {
                default String updateResource(
                        String request,
                        ParentIdT parentId) {
                    return "ok";
                }
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @InterceptorBinding
            @interface TestAnn {
            }

            @Singleton
            @InterceptorBinding(TestAnn.class)
            class TestInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }
            """);
        try {
            Object instance = getBean(context, "annbinding1.MyBean");
            Object interceptor = getBean(context, "annbinding1.TestInterceptor");
            invoke(instance, "test");

            assertTrue(instance instanceof Intercepted);
            assertTrue(getBooleanField(interceptor, "invoked"));
        } finally {
            context.close();
        }
    }

    @Test
    void testMultipleInterceptorBinding() throws Exception {
        ApplicationContext context = buildContext("""
            package multiplebinding;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import io.micronaut.context.annotation.NonBinding;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;
            import jakarta.inject.Singleton;

            @Retention(RUNTIME)
            @InterceptorBinding(kind = InterceptorKind.AROUND)
            @interface Deadly {

            }

            @Retention(RUNTIME)
            @InterceptorBinding(kind = InterceptorKind.AROUND)
            @interface Fast {
            }

            @Retention(RUNTIME)
            @InterceptorBinding(kind = InterceptorKind.AROUND)
            @interface Slow {
            }

            @UFO
            @Inherited
            @Retention(RUNTIME)
            @InterceptorBinding(kind = InterceptorKind.AROUND)
            @interface MissileAnn {
            }

            @Inherited
            @Retention(RUNTIME)
            @InterceptorBinding(kind = InterceptorKind.AROUND)
            @interface UFO {
            }

            @MissileAnn
            interface Missile {
                void fire();
            }

            @Fast
            @Deadly
            @Singleton
            class FastAndDeadlyMissile implements Missile {
                public void fire() {
                }
            }

            @Deadly
            @Singleton
            class DeadlyMissile implements Missile {
                public void fire() {
                }
            }

            @Deadly
            @Singleton
            class GuidedMissile implements Missile {

                @Slow
                public void lockAndFire() {
                }

                @Fast
                public void fire() {
                }

            }

            @Slow
            @Deadly
            @Singleton
            class SlowMissile implements Missile {
                public void fire() {
                }
            }

            @Fast
            @Deadly
            @MissileAnn
            @Singleton
            class FastDeadlyInterceptor implements MethodInterceptor<Object, Object> {
                public boolean intercepted = false;

                @Override public Object intercept(MethodInvocationContext<Object, Object> context) {
                    intercepted = true;
                    return context.proceed();
                }
            }

            @Slow
            @Deadly
            @MissileAnn
            @Singleton
            class SlowDeadlyInterceptor implements MethodInterceptor<Object, Object> {
                public boolean intercepted = false;

                @Override public Object intercept(MethodInvocationContext<Object, Object> context) {
                    intercepted = true;
                    return context.proceed();
                }
            }

            @Deadly
            @UFO
            @Singleton
            class DeadlyInterceptor implements MethodInterceptor<Object, Object> {
                public boolean intercepted = false;

                @Override public Object intercept(MethodInvocationContext<Object, Object> context) {
                    intercepted = true;
                    return context.proceed();
                }

                public void reset() {
                    intercepted = false;
                }
            }

            @UFO
            @Singleton
            class UFOInterceptor implements MethodInterceptor<Object, Object> {
                public boolean intercepted = false;

                @Override public Object intercept(MethodInvocationContext<Object, Object> context) {
                    intercepted = true;
                    return context.proceed();
                }

                public void reset() {
                    intercepted = false;
                }
            }
            """);
        try {
            Object fastDeadlyInterceptor = getBean(context, "multiplebinding.FastDeadlyInterceptor");
            Object slowDeadlyInterceptor = getBean(context, "multiplebinding.SlowDeadlyInterceptor");
            Object deadlyInterceptor = getBean(context, "multiplebinding.DeadlyInterceptor");
            Object ufoInterceptor = getBean(context, "multiplebinding.UFOInterceptor");

            setBooleanField(fastDeadlyInterceptor, "intercepted", false);
            setBooleanField(slowDeadlyInterceptor, "intercepted", false);
            setBooleanField(deadlyInterceptor, "intercepted", false);
            setBooleanField(ufoInterceptor, "intercepted", false);
            Object guidedMissile = getBean(context, "multiplebinding.GuidedMissile");
            invoke(guidedMissile, "fire");

            assertTrue(getBooleanField(fastDeadlyInterceptor, "intercepted"));
            assertFalse(getBooleanField(slowDeadlyInterceptor, "intercepted"));
            assertTrue(getBooleanField(deadlyInterceptor, "intercepted"));
            assertTrue(getBooleanField(ufoInterceptor, "intercepted"));

            setBooleanField(fastDeadlyInterceptor, "intercepted", false);
            setBooleanField(slowDeadlyInterceptor, "intercepted", false);
            setBooleanField(deadlyInterceptor, "intercepted", false);
            setBooleanField(ufoInterceptor, "intercepted", false);
            invoke(guidedMissile, "lockAndFire");

            assertFalse(getBooleanField(fastDeadlyInterceptor, "intercepted"));
            assertTrue(getBooleanField(slowDeadlyInterceptor, "intercepted"));
            assertTrue(getBooleanField(deadlyInterceptor, "intercepted"));
            assertTrue(getBooleanField(ufoInterceptor, "intercepted"));

            setBooleanField(fastDeadlyInterceptor, "intercepted", false);
            setBooleanField(slowDeadlyInterceptor, "intercepted", false);
            setBooleanField(deadlyInterceptor, "intercepted", false);
            setBooleanField(ufoInterceptor, "intercepted", false);
            Object fastAndDeadlyMissile = getBean(context, "multiplebinding.FastAndDeadlyMissile");
            invoke(fastAndDeadlyMissile, "fire");

            assertTrue(getBooleanField(fastDeadlyInterceptor, "intercepted"));
            assertFalse(getBooleanField(slowDeadlyInterceptor, "intercepted"));
            assertTrue(getBooleanField(deadlyInterceptor, "intercepted"));
            assertTrue(getBooleanField(ufoInterceptor, "intercepted"));

            setBooleanField(fastDeadlyInterceptor, "intercepted", false);
            setBooleanField(slowDeadlyInterceptor, "intercepted", false);
            setBooleanField(deadlyInterceptor, "intercepted", false);
            setBooleanField(ufoInterceptor, "intercepted", false);
            Object slowMissile = getBean(context, "multiplebinding.SlowMissile");
            invoke(slowMissile, "fire");

            assertFalse(getBooleanField(fastDeadlyInterceptor, "intercepted"));
            assertTrue(getBooleanField(slowDeadlyInterceptor, "intercepted"));
            assertTrue(getBooleanField(deadlyInterceptor, "intercepted"));
            assertTrue(getBooleanField(ufoInterceptor, "intercepted"));

            setBooleanField(fastDeadlyInterceptor, "intercepted", false);
            setBooleanField(slowDeadlyInterceptor, "intercepted", false);
            setBooleanField(deadlyInterceptor, "intercepted", false);
            setBooleanField(ufoInterceptor, "intercepted", false);
            Object anyMissile = getBean(context, "multiplebinding.DeadlyMissile");
            invoke(anyMissile, "fire");

            assertFalse(getBooleanField(fastDeadlyInterceptor, "intercepted"));
            assertFalse(getBooleanField(slowDeadlyInterceptor, "intercepted"));
            assertTrue(getBooleanField(deadlyInterceptor, "intercepted"));
            assertTrue(getBooleanField(ufoInterceptor, "intercepted"));
        } finally {
            context.close();
        }
    }

    @Test
    void testAnnotationWithJustInterceptorBindingMemberBinding() throws Exception {
        ApplicationContext context = buildContext("""
            package memberbinding;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import io.micronaut.context.annotation.NonBinding;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;
            import jakarta.inject.Singleton;

            @Singleton
            @TestAnn(num=1, debug = false)
            class MyBean {
                void test() {
                }

                @TestAnn(num=2) // overrides binding on type
                void test2() {

                }
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @InterceptorBinding(bindMembers = true)
            @interface TestAnn {
                int num();

                @NonBinding
                boolean debug() default false;
            }

            @InterceptorBean(TestAnn.class)
            @TestAnn(num = 1, debug = true)
            class TestInterceptor implements Interceptor {
                public boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }

            @InterceptorBean(TestAnn.class)
            @TestAnn(num = 2)
            class AnotherInterceptor implements Interceptor {
                public boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }
            """);
        try {
            Object instance = getBean(context, "memberbinding.MyBean");
            Object interceptor = getBean(context, "memberbinding.TestInterceptor");
            Object anotherInterceptor = getBean(context, "memberbinding.AnotherInterceptor");

            invoke(instance, "test");
            assertTrue(instance instanceof Intercepted);
            assertTrue(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(anotherInterceptor, "invoked"));

            setBooleanField(interceptor, "invoked", false);
            invoke(instance, "test2");

            assertFalse(getBooleanField(interceptor, "invoked"));
            assertTrue(getBooleanField(anotherInterceptor, "invoked"));
        } finally {
            context.close();
        }
    }

    @Test
    void testAnnotationWithJustAround() throws Exception {
        ApplicationContext context = buildContext("""
            package justaround;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @Singleton
            @TestAnn
            class MyBean {
                void test() {
                }
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Around
            @interface TestAnn {
            }

            @InterceptorBean(TestAnn.class)
            class TestInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }

            @Singleton
            class AnotherInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }
            """);
        try {
            Object instance = getBean(context, "justaround.MyBean");
            Object interceptor = getBean(context, "justaround.TestInterceptor");
            Object anotherInterceptor = getBean(context, "justaround.AnotherInterceptor");
            invoke(instance, "test");

            assertTrue(instance instanceof Intercepted);
            assertTrue(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(anotherInterceptor, "invoked"));
        } finally {
            context.close();
        }
    }

    @Test
    void testAroundAnnotationOnPrivateMethodFails() {
        Throwable t = assertThrows(Throwable.class, () -> buildContext("""
            package around.priv.method;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @Singleton
            class MyBean {
                @TestAnn
                private void test() {
                }
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Around
            @interface TestAnn {
            }
            """));
        assertTrue(t.getMessage().contains("Method annotated as executable but is declared private"));
    }

    @Test
    void testByteArrayReturnCompile() {
        ApplicationContext context = buildContext("""
            package test;

            import io.micronaut.aop.proxytarget.*;

            @jakarta.inject.Singleton
            @io.micronaut.aop.simple.Mutating("someVal")
            class MyBean {
                byte[] test(byte[] someVal) {
                    return null;
                };
            }
            """);
        try {
            Object instance = getBean(context, "test.MyBean");
            assertNotNull(instance);
        } finally {
            context.close();
        }
    }

    @Test
    void testCompileSimpleAopAdvice() {
        BeanDefinition<?> beanDefinition = buildInterceptedBeanDefinition("test.MyBean", """
            package test;

            import io.micronaut.aop.simple.*;

            @jakarta.inject.Singleton
            @Mutating("someVal")
            @TestBinding
            class MyBean {
                void test() {};
            }
            """);

        BeanDefinitionReference<?> ref = buildInterceptedBeanDefinitionReference("test.MyBean", """
            package test;

            import io.micronaut.aop.simple.*;

            @jakarta.inject.Singleton
            @Mutating("someVal")
            @TestBinding
            class MyBean {
                void test() {};
            }
            """);

        var annotationMetadata = beanDefinition.getAnnotationMetadata();
        List<AnnotationValue<InterceptorBinding>> values = annotationMetadata.getAnnotationValuesByType(InterceptorBinding.class);

        assertEquals(2, values.size());
        assertEquals("io.micronaut.aop.simple.Mutating", values.get(0).stringValue().orElseThrow());
        assertEquals(InterceptorKind.AROUND, values.get(0).enumValue("kind", InterceptorKind.class).orElseThrow());
        assertTrue(values.get(0).classValue("interceptorType").isPresent());
        assertEquals("io.micronaut.aop.simple.TestBinding", values.get(1).stringValue().orElseThrow());
        assertTrue(values.get(1).enumValue("kind", InterceptorKind.class).isPresent());
        assertEquals(InterceptorKind.AROUND, values.get(1).enumValue("kind", InterceptorKind.class).orElseThrow());
        assertFalse(values.get(1).classValue("interceptorType").isPresent());

        assertNotNull(beanDefinition);
        assertTrue(beanDefinition instanceof AdvisedBeanType);
        assertEquals("test.MyBean", ((AdvisedBeanType) beanDefinition).getInterceptedType().getName());
        assertTrue(ref instanceof AdvisedBeanType);
        assertEquals("test.MyBean", ((AdvisedBeanType) ref).getInterceptedType().getName());
    }

    @Test
    void testMultipleAnnotationsOnSingleMethod() throws Exception {
        ApplicationContext context = buildContext("""
            package annbinding2;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;
            import io.micronaut.aop.simple.*;

            @Singleton
            class MyBean {
                @TestAnn
                @TestAnn2
                void test() {

                }

            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Around
            @interface TestAnn {
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Around
            @interface TestAnn2 {
            }

            @InterceptorBean(TestAnn.class)
            class TestInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }

            @InterceptorBean(TestAnn2.class)
            class AnotherInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }
            """);
        try {
            Object instance = getBean(context, "annbinding2.MyBean");
            Object interceptor = getBean(context, "annbinding2.TestInterceptor");
            Object anotherInterceptor = getBean(context, "annbinding2.AnotherInterceptor");

            invoke(instance, "test");

            assertTrue(instance instanceof Intercepted);
            assertTrue(getBooleanField(interceptor, "invoked"));
            assertTrue(getBooleanField(anotherInterceptor, "invoked"));
        } finally {
            context.close();
        }
    }

    @Test
    void testMultipleAnnotationsOnInterceptorAndMethod() throws Exception {
        ApplicationContext context = buildContext("""
            package annbinding2;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;
            import io.micronaut.aop.simple.*;

            @Singleton
            class MyBean {

                @TestAnn
                @TestAnn2
                void test() {

                }

            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Around
            @interface TestAnn {
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Around
            @interface TestAnn2 {
            }


            @InterceptorBean({ TestAnn.class, TestAnn2.class })
            class TestInterceptor implements Interceptor {
                long count = 0;
                @Override
                public Object intercept(InvocationContext context) {
                    count++;
                    return context.proceed();
                }
            }
            """);
        try {
            Object instance = getBean(context, "annbinding2.MyBean");
            Object interceptor = getBean(context, "annbinding2.TestInterceptor");

            invoke(instance, "test");

            assertEquals(1L, getLongField(interceptor, "count"));
        } finally {
            context.close();
        }
    }

    @Test
    void testMultipleAnnotationsOnInterceptor() throws Exception {
        ApplicationContext context = buildContext("""
            package annbinding2;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;
            import io.micronaut.aop.simple.*;

            @Singleton
            class MyBean {

                @TestAnn
                void test() {
                }

                @TestAnn2
                void test2() {
                }

                @TestAnn
                @TestAnn2
                void testBoth() {
                }

            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Around
            @interface TestAnn {
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Around
            @interface TestAnn2 {
            }


            @InterceptorBean({ TestAnn.class, TestAnn2.class })
            class TestInterceptor implements Interceptor {
                long count = 0;
                @Override
                public Object intercept(InvocationContext context) {
                    count++;
                    return context.proceed();
                }
            }
            """);
        try {
            Object instance = getBean(context, "annbinding2.MyBean");
            Object interceptor = getBean(context, "annbinding2.TestInterceptor");

            invoke(instance, "test");
            assertEquals(0L, getLongField(interceptor, "count"));

            invoke(instance, "test2");
            assertEquals(0L, getLongField(interceptor, "count"));

            invoke(instance, "testBoth");
            assertEquals(1L, getLongField(interceptor, "count"));
        } finally {
            context.close();
        }
    }

    @Test
    void testInterceptorOnAnEvent() throws Exception {
        ApplicationContext context = buildContext("""
            package test;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import io.micronaut.context.annotation.Type;
            import io.micronaut.context.event.ApplicationEventListener;
            import io.micronaut.context.event.ApplicationEventPublisher;
            import io.micronaut.core.annotation.Indexed;
            import io.micronaut.core.annotation.Internal;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;
            import io.micronaut.aop.simple.*;
            import jakarta.inject.Singleton;

            class TheEvent {
            }

            @Singleton
            class MyBean {

                private final ApplicationEventPublisher applicationEventPublisher;
                long count = 0;

                MyBean(ApplicationEventPublisher applicationEventPublisher) {
                    this.applicationEventPublisher = applicationEventPublisher;
                }

                void triggerEvent() {
                    applicationEventPublisher.publishEvent(new TheEvent());
                }

                @TransactionalEventListener
                void test(TheEvent theEvent) {
                    count++;
                }

            }

            @Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
            @Retention(RetentionPolicy.RUNTIME)
            @Documented
            @Adapter(ApplicationEventListener.class)
            @Indexed(ApplicationEventListener.class)
            @TransactionalEventAdvice
            @interface TransactionalEventListener {
            }

            @Target(ElementType.ANNOTATION_TYPE)
            @Retention(RetentionPolicy.RUNTIME)
            @Around
            @Type(TransactionalEventInterceptor.class)
            @Internal
            @interface TransactionalEventAdvice {
            }

            @Singleton
            class TransactionalEventInterceptor implements Interceptor {
                long count = 0;
                @Override
                public Object intercept(InvocationContext context) {
                    count++;
                    return context.proceed();
                }

            }
            """);
        try {
            Object service = getBean(context, "test.MyBean");
            Object interceptor = getBean(context, "test.TransactionalEventInterceptor");

            assertEquals(0L, getLongField(interceptor, "count"));

            invoke(service, "triggerEvent");

            assertEquals(1L, getLongField(interceptor, "count"));
            assertEquals(1L, getLongField(service, "count"));
        } finally {
            context.close();
        }
    }

    @Test
    void testValidatedOnClassWithGenerics() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.$BaseEntityService" + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, """
            package test;

            @io.micronaut.validation.Validated
            class BaseEntityService<T extends BaseEntity> extends BaseService<T> {
            }

            class BaseEntity {}
            abstract class BaseService<T> implements IBeanValidator<T> {
                public boolean isValid(T entity) {
                    return true;
                }
            }
            interface IBeanValidator<T> {
                boolean isValid(T entity);
            }
            """);

        assertNotNull(beanDefinition);
        List<?> typeArgs = new ArrayList<>(beanDefinition.getTypeArguments("test.BaseService"));
        assertEquals("test.BaseEntity", beanDefinition.getTypeArguments("test.BaseService").get(0).getType().getName());
    }

    @Test
    void testAroundOnEachProperty() throws Exception {
        ApplicationContext context = buildContext("justaround.MyBean", """
            package justaround;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import io.micronaut.context.annotation.EachProperty;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @EachProperty("somebeans.here")
            class MyBean {
                @TestAnn
                void test() {
                }
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Around
            @interface TestAnn {
            }

            @InterceptorBean(TestAnn.class)
            class TestInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }

            @Singleton
            class AnotherInterceptor implements Interceptor {
                boolean invoked = false;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked = true;
                    return context.proceed();
                }
            }
            """, false, Map.of(
            "somebeans.here.abc", "123",
            "somebeans.here.xyz", "123"
        ));
        try {
            Object instance = getBean(context, "justaround.MyBean", Qualifiers.byName("abc"));
            Object interceptor = getBean(context, "justaround.TestInterceptor");
            Object anotherInterceptor = getBean(context, "justaround.AnotherInterceptor");

            invoke(instance, "test");

            assertTrue(instance instanceof Intercepted);
            assertTrue(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(anotherInterceptor, "invoked"));
        } finally {
            context.close();
        }
    }

    public static class NamedTestAnnMapper implements NamedAnnotationMapper {

        @Override
        public String getName() {
            return "mapperbinding.TestAnn";
        }

        @Override
        public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
            return Collections.singletonList(AnnotationValue.builder(InterceptorBinding.class)
                .value(getName())
                .build());
        }
    }

    public static class TestStereotypeAnnTransformer implements NamedAnnotationTransformer {

        @Override
        public String getName() {
            return "mapperbindingmembers.MyInterceptorBinding";
        }

        @Override
        public List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
            return Collections.singletonList(AnnotationValue.builder(InterceptorBinding.class)
                .member("kind", InterceptorKind.AROUND)
                .member("bindMembers", true)
                .build());
        }
    }


}

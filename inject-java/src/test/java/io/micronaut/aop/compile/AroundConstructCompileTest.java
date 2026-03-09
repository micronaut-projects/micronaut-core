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
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.inject.annotation.NamedAnnotationTransformer;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java port of AroundConstructCompileSpec.
 */
class AroundConstructCompileTest extends AbstractTypeElementTest {

    // Helper to invoke possibly non-public no-arg methods (declared or inherited)
    private static Object invokeNoArg(Object instance, String method) {
        try {
            try {
                var m = instance.getClass().getMethod(method);
                m.setAccessible(true);
                return m.invoke(instance);
            } catch (NoSuchMethodException e) {
                var m = instance.getClass().getDeclaredMethod(method);
                m.setAccessible(true);
                return m.invoke(instance);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // Reflection helpers
    private static boolean getBooleanField(Object obj, String field) {
        Object v = ReflectionUtils.getField(obj.getClass(), field, obj);
        return (v instanceof Boolean b) ? b : Boolean.parseBoolean(String.valueOf(v));
    }

    private static void setBooleanField(Object obj, String field, boolean value) {
        ReflectionUtils.setField(obj.getClass(), field, obj, value);
    }

    private static int getArrayLength(Object obj, String field) {
        Object v = ReflectionUtils.getField(obj.getClass(), field, obj);
        if (v instanceof Object[] arr) {
            return arr.length;
        }
        return 0;
    }

    private static Object getArrayFirstElement(Object obj, String field) {
        Object v = ReflectionUtils.getField(obj.getClass(), field, obj);
        if (v instanceof Object[] arr) {
            return arr.length > 0 ? arr[0] : null;
        }
        return null;
    }

    @Test
    void testAroundConstructWithAnnotationMapperPlusMembers() {
        ApplicationContext context = buildContext("""
            package aroundconstructmapperbindingmembers;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import jakarta.inject.Singleton;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @Singleton
            @TestAnn2
            class MyBean {
                @TestAnn(num=1)
                MyBean() {
                }
            }

            @Retention(RUNTIME)
            @Target({ElementType.ANNOTATION_TYPE})
            @interface MyInterceptorBinding {
            }

            @Retention(RUNTIME)
            @Target({ElementType.CONSTRUCTOR, ElementType.TYPE})
            @MyInterceptorBinding
            @interface TestAnn {
                int num();
            }

            @Retention(RUNTIME)
            @Target({ElementType.CONSTRUCTOR, ElementType.TYPE})
            @MyInterceptorBinding
            @interface TestAnn2 {
            }

            @Singleton
            @TestAnn(num=1)
            class TestInterceptor implements ConstructorInterceptor<Object> {
                public boolean invoked = false;
                @Override
                public Object intercept(ConstructorInvocationContext<Object> context) {
                    invoked = true;
                    return context.proceed();
                }
            }

            @Singleton
            @TestAnn(num=2)
            class TestInterceptor2 implements ConstructorInterceptor<Object> {
                public boolean invoked = false;
                @Override
                public Object intercept(ConstructorInvocationContext<Object> context) {
                    invoked = true;
                    return context.proceed();
                }
            }
            """);
        try {
            Object interceptor = getBean(context, "aroundconstructmapperbindingmembers.TestInterceptor");
            Object interceptor2 = getBean(context, "aroundconstructmapperbindingmembers.TestInterceptor2");

            assertFalse(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(interceptor2, "invoked"));

            getBean(context, "aroundconstructmapperbindingmembers.MyBean");

            assertTrue(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(interceptor2, "invoked"));
        } finally {
            context.close();
        }
    }

    @Test
    void testAroundConstructOnTypeAndConstructorWithProxyTargetBindMembers() {
        ApplicationContext context = buildContext("""
            package ctorbinding;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.Singleton;
            import static java.lang.annotation.ElementType.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @FooClassBinding
            @Singleton
            class Foo {

                @FooCtorBinding
                public Foo() {
                }
            }

            @Target({ TYPE, CONSTRUCTOR })
            @Retention(RUNTIME)
            @Documented
            @InterceptorBinding(kind = InterceptorKind.AROUND, bindMembers = true)
            @InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT, bindMembers = true)
            @interface FooCtorBinding {
            }

            @Target({ TYPE })
            @Retention(RUNTIME)
            @Documented
            @InterceptorBinding(kind = InterceptorKind.AROUND, bindMembers = true)
            @InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT, bindMembers = true)
            @Around(proxyTarget = true)
            @interface FooClassBinding {
            }

            @Singleton
            @FooClassBinding
            class Interceptor1 implements ConstructorInterceptor<Object> {
                public boolean intercepted = false;
                @Override public Object intercept(ConstructorInvocationContext<Object> context) {
                    intercepted = true;
                    return context.proceed();
                }
            }

            @Singleton
            @FooCtorBinding
            class Interceptor2 implements ConstructorInterceptor<Object> {
                public boolean intercepted = false;
                @Override public Object intercept(ConstructorInvocationContext<Object> context) {
                    intercepted = true;
                    return context.proceed();
                }
            }
            """);
        try {
            Object i1 = getBean(context, "ctorbinding.Interceptor1");
            Object i2 = getBean(context, "ctorbinding.Interceptor2");

            assertFalse(getBooleanField(i1, "intercepted"));
            assertFalse(getBooleanField(i2, "intercepted"));

            getBean(context, "ctorbinding.Foo");

            assertTrue(getBooleanField(i1, "intercepted"));
            assertTrue(getBooleanField(i2, "intercepted"));
        } finally {
            context.close();
        }
    }

    @Test
    void testAroundConstructOnTypeAndConstructorWithProxyTarget() {
        ApplicationContext context = buildContext("""
            package ctorbinding;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.Singleton;
            import static java.lang.annotation.ElementType.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @FooClassBinding
            @Singleton
            class Foo {

                @FooCtorBinding
                public Foo() {
                }
            }

            @Target({ TYPE, CONSTRUCTOR })
            @Retention(RUNTIME)
            @Documented
            @InterceptorBinding(kind = InterceptorKind.AROUND)
            @InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
            @interface FooCtorBinding {
            }

            @Target({ TYPE })
            @Retention(RUNTIME)
            @Documented
            @InterceptorBinding(kind = InterceptorKind.AROUND)
            @InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
            @Around(proxyTarget = true)
            @interface FooClassBinding {
            }

            @Singleton
            @FooClassBinding
            class Interceptor1 implements ConstructorInterceptor<Object> {
                public boolean intercepted = false;
                @Override public Object intercept(ConstructorInvocationContext<Object> context) {
                    intercepted = true;
                    return context.proceed();
                }
            }

            @Singleton
            @FooCtorBinding
            class Interceptor2 implements ConstructorInterceptor<Object> {
                public boolean intercepted = false;
                @Override public Object intercept(ConstructorInvocationContext<Object> context) {
                    intercepted = true;
                    return context.proceed();
                }
            }
            """);
        try {
            Object i1 = getBean(context, "ctorbinding.Interceptor1");
            Object i2 = getBean(context, "ctorbinding.Interceptor2");

            assertFalse(getBooleanField(i1, "intercepted"));
            assertFalse(getBooleanField(i2, "intercepted"));

            getBean(context, "ctorbinding.Foo");

            assertTrue(getBooleanField(i1, "intercepted"));
            assertTrue(getBooleanField(i2, "intercepted"));
        } finally {
            context.close();
        }
    }

    @Test
    void testAroundConstructOnTypeAndConstructor() {
        ApplicationContext context = buildContext("""
            package ctorbinding;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.Singleton;
            import static java.lang.annotation.ElementType.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @FooClassBinding
            @Singleton
            class Foo {

                @FooCtorBinding
                public Foo() {
                }
            }

            @Target({ TYPE, CONSTRUCTOR })
            @Retention(RUNTIME)
            @Documented
            @InterceptorBinding(kind = InterceptorKind.AROUND)
            @InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
            @interface FooCtorBinding {
            }

            @Target({ TYPE })
            @Retention(RUNTIME)
            @Documented
            @InterceptorBinding(kind = InterceptorKind.AROUND)
            @InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
            @interface FooClassBinding {
            }

            @Singleton
            @FooClassBinding
            class Interceptor1 implements ConstructorInterceptor<Object> {
                public boolean intercepted = false;
                @Override public Object intercept(ConstructorInvocationContext<Object> context) {
                    intercepted = true;
                    return context.proceed();
                }
            }

            @Singleton
            @FooCtorBinding
            class Interceptor2 implements ConstructorInterceptor<Object> {
                public boolean intercepted = false;
                @Override public Object intercept(ConstructorInvocationContext<Object> context) {
                    intercepted = true;
                    return context.proceed();
                }
            }
            """);
        try {
            Object i1 = getBean(context, "ctorbinding.Interceptor1");
            Object i2 = getBean(context, "ctorbinding.Interceptor2");

            assertFalse(getBooleanField(i1, "intercepted"));
            assertFalse(getBooleanField(i2, "intercepted"));

            getBean(context, "ctorbinding.Foo");

            assertTrue(getBooleanField(i1, "intercepted"));
            assertTrue(getBooleanField(i2, "intercepted"));
        } finally {
            context.close();
        }
    }

    @Test
    void testAroundConstructWithAroundInterceptionProxyTargetTrue() {
        doTestAroundConstructWithAroundInterception(true);
    }

    @Test
    void testAroundConstructWithAroundInterceptionProxyTargetFalse() {
        doTestAroundConstructWithAroundInterception(false);
    }

    private void doTestAroundConstructWithAroundInterception(boolean proxyTarget) {
        ApplicationContext context = buildContext("""
            package annbinding1;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @Singleton
            @TestAnn
            class MyBean {
                MyBean(io.micronaut.context.env.Environment env) {}
                void test() {
                }
            }

            @io.micronaut.context.annotation.Factory
            class MyFactory {
                @TestAnn
                @Singleton
                MyOtherBean test(io.micronaut.context.env.Environment env) {
                    return new MyOtherBean();
                }
            }

            class MyOtherBean {}

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Around(proxyTarget=%s)
            @AroundConstruct
            @interface TestAnn {
            }

            @Singleton
            @InterceptorBean(TestAnn.class)
            class TestConstructInterceptor implements ConstructorInterceptor<Object> {
                boolean invoked = false;
                Object[] parameters;

                @Override
                public Object intercept(ConstructorInvocationContext<Object> context) {
                    invoked = true;
                    parameters = context.getParameterValues();
                    return context.proceed();
                }
            }

            @Singleton
            @InterceptorBean(TestAnn.class)
            class TypeSpecificConstructInterceptor implements ConstructorInterceptor<MyBean> {
                boolean invoked = false;
                Object[] parameters;

                @Override
                public MyBean intercept(ConstructorInvocationContext<MyBean> context) {
                    invoked = true;
                    parameters = context.getParameterValues();
                    MyBean mb = context.proceed();
                    return mb;
                }
            }

            @Singleton
            @InterceptorBinding(TestAnn.class)
            class TestInterceptor implements MethodInterceptor {
                boolean invoked = false;
                @Override
                public Object intercept(MethodInvocationContext context) {
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
            """.formatted(proxyTarget ? "true" : "false"));
        try {
            Object interceptor = getBean(context, "annbinding1.TestInterceptor");
            Object constructorInterceptor = getBean(context, "annbinding1.TestConstructInterceptor");
            Object typeSpecificInterceptor = getBean(context, "annbinding1.TypeSpecificConstructInterceptor");
            Object anotherInterceptor = getBean(context, "annbinding1.AnotherInterceptor");

            assertFalse(getBooleanField(constructorInterceptor, "invoked"));
            assertFalse(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(anotherInterceptor, "invoked"));

            Object instance = getBean(context, "annbinding1.MyBean");

            assertTrue(getBooleanField(constructorInterceptor, "invoked"));
            assertTrue(getBooleanField(typeSpecificInterceptor, "invoked"));
            assertEquals(1, getArrayLength(constructorInterceptor, "parameters"));

            // other non-constructor interceptors are not invoked yet
            assertFalse(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(anotherInterceptor, "invoked"));

            // invoke method with interception
            setBooleanField(constructorInterceptor, "invoked", false);
            setBooleanField(typeSpecificInterceptor, "invoked", false);
            invokeNoArg(instance, "test");

            assertTrue(instance instanceof Intercepted);
            assertTrue(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(anotherInterceptor, "invoked"));

            // constructor interceptor not invoked on method call
            assertFalse(getBooleanField(constructorInterceptor, "invoked"));
            assertFalse(getBooleanField(typeSpecificInterceptor, "invoked"));

            // bean created from factory is intercepted on construction only
            setBooleanField(constructorInterceptor, "invoked", false);
            setBooleanField(interceptor, "invoked", false);
            Object factoryCreatedInstance = getBean(context, "annbinding1.MyOtherBean");

            assertTrue(getBooleanField(constructorInterceptor, "invoked"));
            assertFalse(getBooleanField(typeSpecificInterceptor, "invoked"));
            assertEquals(1, getArrayLength(constructorInterceptor, "parameters"));
            assertFalse(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(anotherInterceptor, "invoked"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            context.close();
        }
    }

    @Test
    void testAroundConstructWithoutAroundInterception() {
        ApplicationContext context = buildContext("""
            package annbinding1;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @Singleton
            @TestAnn
            class MyBean {
                MyBean(io.micronaut.context.env.Environment env) {}
                void test() {
                }
            }

            @io.micronaut.context.annotation.Factory
            class MyFactory {
                @TestAnn
                @Singleton
                MyOtherBean test(io.micronaut.context.env.Environment env) {
                    return new MyOtherBean();
                }
            }

            class MyOtherBean {}

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @AroundConstruct
            @interface TestAnn {
            }

            @Singleton
            @InterceptorBean(TestAnn.class)
            class TestConstructInterceptor implements ConstructorInterceptor<Object> {
                boolean invoked = false;
                Object[] parameters;

                @Override
                public Object intercept(ConstructorInvocationContext<Object> context) {
                    invoked = true;
                    parameters = context.getParameterValues();
                    return context.proceed();
                }
            }

            @Singleton
            @InterceptorBinding(TestAnn.class)
            class TestInterceptor implements MethodInterceptor {
                boolean invoked = false;
                @Override
                public Object intercept(MethodInvocationContext context) {
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
            Object interceptor = getBean(context, "annbinding1.TestInterceptor");
            Object constructorInterceptor = getBean(context, "annbinding1.TestConstructInterceptor");
            Object anotherInterceptor = getBean(context, "annbinding1.AnotherInterceptor");

            assertFalse(getBooleanField(constructorInterceptor, "invoked"));
            assertFalse(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(anotherInterceptor, "invoked"));

            Object instance = getBean(context, "annbinding1.MyBean");

            assertFalse(instance instanceof Intercepted);
            assertTrue(getBooleanField(constructorInterceptor, "invoked"));
            assertEquals(1, getArrayLength(constructorInterceptor, "parameters"));

            assertFalse(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(anotherInterceptor, "invoked"));

            setBooleanField(constructorInterceptor, "invoked", false);
            invokeNoArg(instance, "test");

            assertFalse(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(anotherInterceptor, "invoked"));
            assertFalse(getBooleanField(constructorInterceptor, "invoked"));

            setBooleanField(constructorInterceptor, "invoked", false);
            setBooleanField(interceptor, "invoked", false);
            Object factoryCreatedInstance = getBean(context, "annbinding1.MyOtherBean");

            assertFalse(factoryCreatedInstance instanceof Intercepted);
            assertTrue(getBooleanField(constructorInterceptor, "invoked"));
            assertEquals(1, getArrayLength(constructorInterceptor, "parameters"));
            assertFalse(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(anotherInterceptor, "invoked"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            context.close();
        }
    }

    @Test
    void testAroundConstructDeclaredOnConstructorOnly() {
        ApplicationContext context = buildContext("""
            package annbinding1;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @Singleton
            class MyBean {
                @TestAnn
                MyBean(io.micronaut.context.env.Environment env) {}

                void test() {
                }
            }

            @Retention(RUNTIME)
            @Target({ElementType.CONSTRUCTOR})
            @AroundConstruct
            @Around
            @interface TestAnn {
            }

            @Singleton
            @InterceptorBean(TestAnn.class)
            class TestConstructInterceptor implements ConstructorInterceptor<Object> {
                boolean invoked = false;
                Object[] parameters;

                @Override
                public Object intercept(ConstructorInvocationContext<Object> context) {
                    invoked = true;
                    parameters = context.getParameterValues();
                    return context.proceed();
                }
            }
            """);
        try {
            Object constructorInterceptor = getBean(context, "annbinding1.TestConstructInterceptor");
            assertFalse(getBooleanField(constructorInterceptor, "invoked"));

            Object instance = getBean(context, "annbinding1.MyBean");

            assertFalse(instance instanceof Intercepted);
            assertTrue(getBooleanField(constructorInterceptor, "invoked"));
            assertEquals(1, getArrayLength(constructorInterceptor, "parameters"));
        } finally {
            context.close();
        }
    }

    @Test
    void testAroundConstructWithoutAroundInterceptionInterceptorsFromFactory() {
        ApplicationContext context = buildContext("""
            package annbinding1;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import io.micronaut.context.annotation.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @Singleton
            @TestAnn
            class MyBean {
                MyBean(io.micronaut.context.env.Environment env) {}
                void test() {
                }
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @AroundConstruct
            @interface TestAnn {
            }

            @Factory
            class InterceptorFactory {
                boolean aroundConstructInvoked = false;

                @InterceptorBean(TestAnn.class)
                ConstructorInterceptor<Object> aroundIntercept() {
                    return (context) -> {
                        this.aroundConstructInvoked = true;
                        return context.proceed();
                    };
                }
            }
            """);
        try {
            Object factory = getBean(context, "annbinding1.InterceptorFactory");
            assertFalse(getBooleanField(factory, "aroundConstructInvoked"));

            Object instance = getBean(context, "annbinding1.MyBean");

            assertFalse(instance instanceof Intercepted);
            assertTrue(getBooleanField(factory, "aroundConstructInvoked"));
        } finally {
            context.close();
        }
    }

    @Test
    void testAroundConstructWithIntroductionAdvice() {
        ApplicationContext context = buildContext("""
            package annbinding1;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @Singleton
            @TestAnn
            abstract class MyBean {
                MyBean(io.micronaut.context.env.Environment env) {}
                abstract String test();
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Introduction
            @AroundConstruct
            @interface TestAnn {
            }

            @Singleton
            @InterceptorBean(TestAnn.class)
            class TestConstructInterceptor implements ConstructorInterceptor<Object> {
                boolean invoked = false;
                Object[] parameters;

                @Override
                public Object intercept(ConstructorInvocationContext<Object> context) {
                    invoked = true;
                    parameters = context.getParameterValues();
                    return context.proceed();
                }
            }

            @Singleton
            @InterceptorBinding(TestAnn.class)
            class TestInterceptor implements MethodInterceptor {
                boolean invoked = false;
                @Override
                public Object intercept(MethodInvocationContext context) {
                    invoked = true;
                    return "good";
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
            Object interceptor = getBean(context, "annbinding1.TestInterceptor");
            Object constructorInterceptor = getBean(context, "annbinding1.TestConstructInterceptor");
            Object anotherInterceptor = getBean(context, "annbinding1.AnotherInterceptor");

            assertFalse(getBooleanField(constructorInterceptor, "invoked"));
            assertFalse(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(anotherInterceptor, "invoked"));

            Object instance = getBean(context, "annbinding1.MyBean");

            assertTrue(instance instanceof Intercepted);
            assertTrue(getBooleanField(constructorInterceptor, "invoked"));
            assertEquals(1, getArrayLength(constructorInterceptor, "parameters"));
            Object firstParam = getArrayFirstElement(constructorInterceptor, "parameters");
            assertTrue(firstParam instanceof Environment);

            assertFalse(getBooleanField(interceptor, "invoked"));
            assertFalse(getBooleanField(anotherInterceptor, "invoked"));

            setBooleanField(constructorInterceptor, "invoked", false);
            String result = (String) invokeNoArg(instance, "test");

            assertTrue(getBooleanField(interceptor, "invoked"));
            assertEquals("good", result);
            assertFalse(getBooleanField(anotherInterceptor, "invoked"));
            assertFalse(getBooleanField(constructorInterceptor, "invoked"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            context.close();
        }
    }

    // Register local transformer for aroundconstructmapperbindingmembers.MyInterceptorBinding
    @Override
    protected java.util.List<io.micronaut.inject.annotation.AnnotationTransformer<? extends java.lang.annotation.Annotation>> getLocalAnnotationTransformers(String annotationName) {
        if ("aroundconstructmapperbindingmembers.MyInterceptorBinding".equals(annotationName)) {
            return java.util.List.of(new TestStereotypeAnnTransformer());
        }
        return java.util.Collections.emptyList();
    }

    // Mirrors the Groovy spec: only binds to AROUND_CONSTRUCT with bindMembers=true.
    public static final class TestStereotypeAnnTransformer implements NamedAnnotationTransformer {

        @Override
        public String getName() {
            return "aroundconstructmapperbindingmembers.MyInterceptorBinding";
        }

        @Override
        public java.util.List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
            return java.util.List.of(
                AnnotationValue.builder(InterceptorBinding.class)
                    .member("kind", InterceptorKind.AROUND_CONSTRUCT)
                    .member("bindMembers", true)
                    .build()
            );
        }
    }
}

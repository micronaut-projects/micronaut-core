package io.micronaut.aop.introduction.beans;

import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.test.AbstractTypeElementTest;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class IntroducedBeanVisitorTest extends AbstractTypeElementTest {

    @Test
    void testIntroducedBeanVisitor1() {
        var context = buildContext("""
            package introducedbeanspec;

            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.*;
            import io.micronaut.aop.Introduction;
            import io.micronaut.context.annotation.Type;
            import org.jspecify.annotations.NonNull;
            import io.micronaut.aop.MethodInterceptor;
            import io.micronaut.aop.MethodInvocationContext;
            import org.jspecify.annotations.Nullable;
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
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
            }
            """);

        try {
            Class<?> repo = context.getClassLoader().loadClass("introducedbeanspec.Repo3");
            BeanDefinition<?> beanDef1 = context.getBeanDefinition(repo);
            var findAllMethod = beanDef1.getRequiredMethod("findAll");
            var method1 = beanDef1.getRequiredMethod("method1");
            var method2 = beanDef1.getRequiredMethod("method2");
            var method3 = beanDef1.getRequiredMethod("method3");

            assertTrue(findAllMethod.hasAnnotation("introducedbeanspec.XMyDataMethod"));
            assertTrue(method1.hasAnnotation("introducedbeanspec.XMyDataMethod"));
            assertTrue(method2.hasAnnotation("introducedbeanspec.XMyDataMethod"));
            assertTrue(method3.hasAnnotation("introducedbeanspec.XMyDataMethod"));
        } catch (ClassNotFoundException e) {
            fail(e);
        } finally {
            context.close();
        }
    }

    @Test
    void testIntroducedBeanVisitor2() {
        var context = buildContext("""
            package introducedbeanspec2;

            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.*;
            import io.micronaut.aop.InterceptorBean;
            import io.micronaut.aop.Introduction;
            import io.micronaut.context.annotation.Type;
            import org.jspecify.annotations.NonNull;
            import io.micronaut.aop.MethodInterceptor;
            import io.micronaut.aop.MethodInvocationContext;
            import org.jspecify.annotations.Nullable;
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
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
            }
            """);

        try {
            Class<?> repo = context.getClassLoader().loadClass("introducedbeanspec2.Repo3");
            BeanDefinition<?> beanDef1 = context.getBeanDefinition(repo);
            var findAllMethod = beanDef1.getRequiredMethod("findAll");
            var method1 = beanDef1.getRequiredMethod("method1");
            var method2 = beanDef1.getRequiredMethod("method2");
            var method3 = beanDef1.getRequiredMethod("method3");

            assertTrue(findAllMethod.hasAnnotation("introducedbeanspec2.XMyDataMethod"));
            assertTrue(method1.hasAnnotation("introducedbeanspec2.XMyDataMethod"));
            assertTrue(method2.hasAnnotation("introducedbeanspec2.XMyDataMethod"));
            assertTrue(method3.hasAnnotation("introducedbeanspec2.XMyDataMethod"));
        } catch (ClassNotFoundException e) {
            fail(e);
        } finally {
            context.close();
        }
    }

    @Test
    void testIntroducedBeanVisitor3() {
        var context = buildContext("""
            package introducedbeanspec;

            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.*;
            import io.micronaut.aop.Introduction;
            import io.micronaut.context.annotation.Type;
            import org.jspecify.annotations.NonNull;
            import io.micronaut.aop.MethodInterceptor;
            import io.micronaut.aop.MethodInvocationContext;
            import org.jspecify.annotations.Nullable;
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
                void findAll(org.reactivestreams.Publisher<MyBean> publisher);
                void method1(org.reactivestreams.Publisher<MyBean> publisher);
            }

            interface Repo2<E> {
                void findAll(org.reactivestreams.Publisher<E> e);
                void method2(org.reactivestreams.Publisher<E> e);
            }

            @RepoDef
            interface Repo3 extends Repo2<MyBean>, Repo1 {
                void method3(org.reactivestreams.Publisher<MyBean> p);
            }

            class MyBean {
                private String name;
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
            }
            """);

        try {
            Class<?> repo = context.getClassLoader().loadClass("introducedbeanspec.Repo3");
            BeanDefinition<?> beanDef1 = context.getBeanDefinition(repo);
            var findAllMethod = beanDef1.getRequiredMethod("findAll", Publisher.class);
            var method1 = beanDef1.getRequiredMethod("method1", Publisher.class);
            var method2 = beanDef1.getRequiredMethod("method2", Publisher.class);
            var method3 = beanDef1.getRequiredMethod("method3", Publisher.class);

            assertTrue(findAllMethod.hasAnnotation("introducedbeanspec.XMyDataMethod"));
            assertTrue(method1.hasAnnotation("introducedbeanspec.XMyDataMethod"));
            assertTrue(method2.hasAnnotation("introducedbeanspec.XMyDataMethod"));
            assertTrue(method3.hasAnnotation("introducedbeanspec.XMyDataMethod"));
        } catch (ClassNotFoundException e) {
            fail(e);
        } finally {
            context.close();
        }
    }

    @Test
    void testIntroducedBeanVisitor4() {
        var context = buildContext("""
            package introducedbeanspec;

            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.*;
            import io.micronaut.aop.Introduction;
            import io.micronaut.context.annotation.Type;
            import org.jspecify.annotations.NonNull;
            import io.micronaut.aop.MethodInterceptor;
            import io.micronaut.aop.MethodInvocationContext;
            import org.jspecify.annotations.Nullable;
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
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
            }
            """);

        try {
            Class<?> repo = context.getClassLoader().loadClass("introducedbeanspec.Repo3");
            Class<?> clazz = context.getClassLoader().loadClass("introducedbeanspec.MyBean");
            BeanDefinition<?> beanDef1 = context.getBeanDefinition(repo);
            var findAllMethod = beanDef1.getRequiredMethod("findAll", clazz);
            var method1 = beanDef1.getRequiredMethod("method1", clazz);
            var method2 = beanDef1.getRequiredMethod("method2", clazz);
            var method3 = beanDef1.getRequiredMethod("method3", clazz);

            assertTrue(findAllMethod.hasAnnotation("introducedbeanspec.XMyDataMethod"));
            assertTrue(method1.hasAnnotation("introducedbeanspec.XMyDataMethod"));
            assertTrue(method2.hasAnnotation("introducedbeanspec.XMyDataMethod"));
            assertTrue(method3.hasAnnotation("introducedbeanspec.XMyDataMethod"));
        } catch (ClassNotFoundException e) {
            fail(e);
        } finally {
            context.close();
        }
    }
}

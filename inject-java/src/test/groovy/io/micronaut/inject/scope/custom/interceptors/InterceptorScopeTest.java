package io.micronaut.inject.scope.custom.interceptors;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.context.scope.AbstractConcurrentCustomScope;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.runtime.context.scope.ScopedProxy;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InterceptorScopeTest {

    @Test
    void testInterceptorsWithCustomScopes() {
        try (ApplicationContext beanContext = ApplicationContext.run()) {

            MyCustomScope scope = beanContext.getBean(MyCustomScope.class);

            MyScopeKey key1 = new MyScopeKey();
            MyScopeKey key2 = new MyScopeKey();

            scope.setKey(key1);

            assertNull(
                    key1.scopedBeans
            );
            final MyBean bean1 = beanContext.getBean(MyBean.class);
            final String result1 = bean1.printHelloWorld();
            // invoke again
            bean1.printHelloWorld();

            assertNotNull(key1.scopedBeans);
            assertEquals(
                    1,
                    key1.scopedBeans.size()
            );
            scope.setKey(key2);

            final MyBean bean2 = beanContext.getBean(MyBean.class);
            final String result2 = bean2.printHelloWorld();

            assertEquals(
                    1,
                    key1.scopedBeans.size()
            );
            assertEquals(
                    1,
                    key2.scopedBeans.size()
            );

            // the prototype interceptor should inject a new instance into
            // each newly created bean
            assertEquals(
                    "Hello World! Interceptor2 created: [1]  Interceptor1 created: [1] ",
                    result1
            );
            assertEquals(
                    "Hello World! Interceptor2 created: [1]  Interceptor1 created: [2] ",
                    result2
            );
        }

    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Scope
    @ScopedProxy
    @interface MyScope { }

    static class MyScopeKey {
        private ConcurrentHashMap<BeanIdentifier, CreatedBean<?>> scopedBeans;

        ConcurrentHashMap<BeanIdentifier, CreatedBean<?>> getScopedBeans() {
            return scopedBeans;
        }

        void setScopedBeans(ConcurrentHashMap<BeanIdentifier, CreatedBean<?>> scopedBeans) {
            this.scopedBeans = scopedBeans;
        }
    }

    static class NoMyScopedKeyException extends NoSuchBeanException {

        protected NoMyScopedKeyException() {
            super("No my scoped key present");
        }
    }

    @Singleton
    static class MyCustomScope extends AbstractConcurrentCustomScope<MyScope> {

        private static final ThreadLocal<MyScopeKey> MY_SCOPE_KEY = new ThreadLocal<>();

        MyCustomScope() {
            super(MyScope.class);
        }

        @Override
        protected Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean forCreation) {
            MyScopeKey key = MY_SCOPE_KEY.get();

            if (key == null) {
                throw new NoMyScopedKeyException();
            }

            ConcurrentHashMap<BeanIdentifier, CreatedBean<?>> scopedBeans = key.getScopedBeans();

            if (scopedBeans != null) {
                // Return existing map, if present.
                return scopedBeans;
            }

            if (forCreation) {
                scopedBeans = new ConcurrentHashMap<>(5);
                key.setScopedBeans(scopedBeans);
            }

            assert scopedBeans != null;
            return scopedBeans;
        }

        @Override
        public boolean isRunning() {
            return MY_SCOPE_KEY.get() != null;
        }

        @Override
        public void close() {
            // Not relevant.
        }

        void setKey(MyScopeKey key) {
            MY_SCOPE_KEY.set(key);
        }
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Around
    @Inherited
    public @interface MyIntercepted1 {
    }

    @Prototype
    @InterceptorBean(MyIntercepted1.class)
    static class MyIntercepted1Interceptor implements MethodInterceptor<Object, Object> {
        private static int creationCount = 0;
        MyIntercepted1Interceptor() {
            creationCount++;
        }

        @Override
        public Object intercept(MethodInvocationContext<Object, Object> context) {
            return context.proceed() + " Interceptor1 created: [" + creationCount + "] ";
        }
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Around
    @Inherited
    public @interface MyIntercepted2 {
    }

    @Singleton
    @InterceptorBean(MyIntercepted2.class)
    static class MyIntercepted2Interceptor implements MethodInterceptor<Object, Object> {
        private static int creationCount = 0;
        MyIntercepted2Interceptor() {
            creationCount++;
        }

        @Override
        public Object intercept(MethodInvocationContext<Object, Object> context) {
            return context.proceed() + " Interceptor2 created: [" + creationCount + "] ";
        }
    }

    @Bean
    @MyScope
    static class MyBean {

        MyBean() {
            System.out.println("My bean constructor");
        }

        @MyIntercepted1
        @MyIntercepted2
        String printHelloWorld() {
            return "Hello World!";
        }
    }
}

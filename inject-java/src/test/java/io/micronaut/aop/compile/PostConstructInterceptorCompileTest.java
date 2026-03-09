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
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.core.reflect.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Java port of PostConstructInterceptorCompileSpec.
 */
class PostConstructInterceptorCompileTest extends AbstractTypeElementTest {

    // Reflection helpers
    private static int getIntField(Object obj, String field) {
        try {
            Object v = ReflectionUtils.getField(obj.getClass(), field, obj);
            if (v instanceof Number n) {
                return n.intValue();
            }
            return Integer.parseInt(String.valueOf(v));
        } catch (Throwable ignore) {
            // Fallback: unwrap proxies to the intercepted target if present
            try {
                var m = obj.getClass().getMethod("interceptedTarget");
                m.setAccessible(true);
                Object target = m.invoke(obj);
                Object v = ReflectionUtils.getField(target.getClass(), field, target);
                if (v instanceof Number n) {
                    return n.intValue();
                }
                return Integer.parseInt(String.valueOf(v));
            } catch (ReflectiveOperationException e2) {
                throw new RuntimeException(e2);
            }
        }
    }

    private static Object invoke(Object bean, String name, Class<?>... argTypes) {
        try {
            try {
                var m = bean.getClass().getMethod(name, argTypes);
                m.setAccessible(true);
                return m.invoke(bean);
            } catch (NoSuchMethodException e) {
                var m = bean.getClass().getDeclaredMethod(name, argTypes);
                m.setAccessible(true);
                return m.invoke(bean);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testPostConstructWithAroundInterception_proxyTargetTrue() {
        doTestPostConstructWithAroundInterception(true);
    }

    @Test
    void testPostConstructWithAroundInterception_proxyTargetFalse() {
        doTestPostConstructWithAroundInterception(false);
    }

    private void doTestPostConstructWithAroundInterception(boolean proxyTarget) {
        ApplicationContext context = buildContext("""
            package annbinding1;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;
            import javax.annotation.*;

            @Singleton
            @TestAnn
            class MyBean {
                @Inject io.micronaut.context.env.Environment env;
                int invoked;

                MyBean(io.micronaut.context.env.Environment env) {}
                void test() {
                }

                @PostConstruct
                void init() {
                    this.invoked++;
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
            @InterceptorBinding(kind=InterceptorKind.POST_CONSTRUCT)
            @InterceptorBinding(kind=InterceptorKind.PRE_DESTROY)
            @interface TestAnn {
            }

            @Singleton
            @InterceptorBean(TestAnn.class)
            class TestInterceptor implements MethodInterceptor {
                int invoked;
                @Override
                public Object intercept(MethodInvocationContext context) {
                    invoked++;
                    return context.proceed();
                }
            }

            @Singleton
            @InterceptorBinding(value=TestAnn.class, kind=InterceptorKind.POST_CONSTRUCT)
            class PostConstructTestInterceptor implements MethodInterceptor {
                int invoked;
                @Override
                public Object intercept(MethodInvocationContext context) {
                    invoked++;
                    return context.proceed();
                }
            }

            @Singleton
            @InterceptorBinding(value=TestAnn.class, kind=InterceptorKind.PRE_DESTROY)
            class PreDestroyTestInterceptor implements MethodInterceptor {
                int invoked;
                @Override
                public Object intercept(MethodInvocationContext context) {
                    invoked++;
                    return context.proceed();
                }
            }

            @Singleton
            class AnotherInterceptor implements Interceptor {
                int invoked;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked++;
                    return context.proceed();
                }
            }
            """.formatted(proxyTarget ? "true" : "false"));
        try {
            Object interceptor = getBean(context, "annbinding1.TestInterceptor");
            Object constructorInterceptor = getBean(context, "annbinding1.PostConstructTestInterceptor");
            Object destroyInterceptor = getBean(context, "annbinding1.PreDestroyTestInterceptor");
            Object anotherInterceptor = getBean(context, "annbinding1.AnotherInterceptor");

            assertEquals(0, getIntField(interceptor, "invoked"));
            assertEquals(0, getIntField(anotherInterceptor, "invoked"));
            assertEquals(0, getIntField(constructorInterceptor, "invoked"));

            Object instance = getBean(context, "annbinding1.MyBean");

            // post construct invoked
            int beanInvoked = getIntField(proxyTarget ? invoke(instance, "interceptedTarget") : instance, "invoked");
            assertEquals(1, beanInvoked);
            assertEquals(1, getIntField(interceptor, "invoked"));
            assertEquals(1, getIntField(constructorInterceptor, "invoked"));
            assertEquals(0, getIntField(anotherInterceptor, "invoked"));
            assertEquals(0, getIntField(destroyInterceptor, "invoked"));

            // method invocation
            invoke(instance, "test");
            org.junit.jupiter.api.Assertions.assertInstanceOf(Intercepted.class, instance);
            assertEquals(2, getIntField(interceptor, "invoked"));
            assertEquals(1, getIntField(constructorInterceptor, "invoked"));
            assertEquals(0, getIntField(anotherInterceptor, "invoked"));

            // factory created instance
            Object factoryCreatedInstance = getBean(context, "annbinding1.MyOtherBean");
            assertNotNull(factoryCreatedInstance);
            assertEquals(3, getIntField(interceptor, "invoked"));
            assertEquals(2, getIntField(constructorInterceptor, "invoked"));
            assertEquals(0, getIntField(anotherInterceptor, "invoked"));

            context.stop();

            // destroy counts
            assertEquals(proxyTarget ? 5 : 4, getIntField(interceptor, "invoked"));
            assertEquals(2, getIntField(constructorInterceptor, "invoked"));
            assertEquals(0, getIntField(anotherInterceptor, "invoked"));
            assertEquals(proxyTarget ? 2 : 1, getIntField(destroyInterceptor, "invoked"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testPostConstructAndPreDestroyWithoutAroundInterception() {
        ApplicationContext context = buildContext("""
            package annbinding1;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;
            import javax.annotation.*;

            @Singleton
            @TestAnn
            class MyBean {
                @Inject io.micronaut.context.env.Environment env;
                int invoked;

                MyBean(io.micronaut.context.env.Environment env) {}
                void test() {
                }

                @PostConstruct
                void init() {
                    this.invoked++;
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
            @InterceptorBinding(kind=InterceptorKind.POST_CONSTRUCT)
            @InterceptorBinding(kind=InterceptorKind.PRE_DESTROY)
            @interface TestAnn {
            }

            @Singleton
            @InterceptorBean(TestAnn.class)
            class TestInterceptor implements MethodInterceptor {
                int invoked;
                @Override
                public Object intercept(MethodInvocationContext context) {
                    invoked++;
                    return context.proceed();
                }
            }

            @Singleton
            @InterceptorBinding(value=TestAnn.class, kind=InterceptorKind.POST_CONSTRUCT)
            class PostConstructTestInterceptor implements MethodInterceptor {
                int invoked;
                @Override
                public Object intercept(MethodInvocationContext context) {
                    invoked++;
                    return context.proceed();
                }
            }

            @Singleton
            @InterceptorBinding(value=TestAnn.class, kind=InterceptorKind.PRE_DESTROY)
            class PreDestroyTestInterceptor implements MethodInterceptor {
                int invoked;
                @Override
                public Object intercept(MethodInvocationContext context) {
                    invoked++;
                    return context.proceed();
                }
            }

            @Singleton
            class AnotherInterceptor implements Interceptor {
                int invoked;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked++;
                    return context.proceed();
                }
            }
            """);
        try {
            Object interceptor = getBean(context, "annbinding1.TestInterceptor");
            Object constructorInterceptor = getBean(context, "annbinding1.PostConstructTestInterceptor");
            Object destroyInterceptor = getBean(context, "annbinding1.PreDestroyTestInterceptor");
            Object anotherInterceptor = getBean(context, "annbinding1.AnotherInterceptor");

            assertEquals(0, getIntField(interceptor, "invoked"));
            assertEquals(0, getIntField(anotherInterceptor, "invoked"));
            assertEquals(0, getIntField(constructorInterceptor, "invoked"));

            Object instance = getBean(context, "annbinding1.MyBean");

            assertEquals(1, getIntField(interceptor, "invoked"));
            assertEquals(1, getIntField(instance, "invoked"));
            assertEquals(1, getIntField(constructorInterceptor, "invoked"));
            assertEquals(0, getIntField(anotherInterceptor, "invoked"));
            assertEquals(0, getIntField(destroyInterceptor, "invoked"));

            invoke(instance, "test");

            assertEquals(1, getIntField(interceptor, "invoked"));
            assertEquals(1, getIntField(constructorInterceptor, "invoked"));
            assertEquals(0, getIntField(anotherInterceptor, "invoked"));

            Object factoryCreatedInstance = getBean(context, "annbinding1.MyOtherBean");
            assertNotNull(factoryCreatedInstance);

            assertEquals(2, getIntField(interceptor, "invoked"));
            assertEquals(2, getIntField(constructorInterceptor, "invoked"));
            assertEquals(0, getIntField(anotherInterceptor, "invoked"));

            context.stop();

            assertEquals(4, getIntField(interceptor, "invoked"));
            assertEquals(2, getIntField(constructorInterceptor, "invoked"));
            assertEquals(0, getIntField(anotherInterceptor, "invoked"));
            assertEquals(2, getIntField(destroyInterceptor, "invoked"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

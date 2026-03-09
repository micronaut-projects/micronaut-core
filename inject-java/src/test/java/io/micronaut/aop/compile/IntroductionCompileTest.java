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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of IntroductionCompileSpec (Spock) to JUnit 5.
 */
class IntroductionCompileTest extends AbstractTypeElementTest {

    // Helper for accessing fields in generated interceptor classes
    private static int getIntField(Object obj, String field) {
        Object v = ReflectionUtils.getField(obj.getClass(), field, obj);
        return (v instanceof Number n) ? n.intValue() : Integer.parseInt(String.valueOf(v));
    }

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
                var m = bean.getClass().getDeclaredMethod(name, argTypes);
                m.setAccessible(true);
                return m.invoke(bean, args);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testInheritedDefaultMethodsAreNotOverridden() throws Exception {
        ApplicationContext context = buildContext("""
            package introductiontest;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @TestAnn
            interface MyBean extends Parent {

                int test();

                default String getName() {
                    return "my-bean";
                }

                @Override
                default String getDescription() {
                    return "description";
                }
            }

            interface Parent {
                default String getParentName() {
                    return "parent";
                }

                String getDescription();
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Introduction
            @interface TestAnn {
            }

            @InterceptorBean(TestAnn.class)
            class StubIntroduction implements Interceptor {
                int invoked = 0;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked++;
                    return 10;
                }
            }
            """);
        try {
            Object instance = getBean(context, "introductiontest.MyBean");
            Object interceptor = getBean(context, "introductiontest.StubIntroduction");

            int result = (Integer) invoke(instance, "test");

            org.junit.jupiter.api.Assertions.assertInstanceOf(Intercepted.class, instance);
            assertEquals("my-bean", invoke(instance, "getName"));
            assertEquals("description", invoke(instance, "getDescription"));
            assertEquals("parent", invoke(instance, "getParentName"));
            assertEquals(10, result);
            assertEquals(1, getIntField(interceptor, "invoked"));
        } finally {
            context.close();
        }
    }

    @Test
    void testInheritedDefaultOrAbstractMethodsAreNotOverridden() throws Exception {
        ApplicationContext context = buildContext("""
            package introductiontest;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @TestAnn
            abstract class MyBean extends Parent implements MyInterface {

                abstract int test();

                public String getName() {
                    return "my-bean";
                }

                @Override
                public String getDescription() {
                    return "description";
                }
            }

            abstract class Parent {
                public String getParentName() {
                    return "parent";
                }

                abstract String getDescription();
            }

            interface MyInterface {
                String getDescription();
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Introduction
            @interface TestAnn {
            }

            @InterceptorBean(TestAnn.class)
            class StubIntroduction implements Interceptor {
                int invoked = 0;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked++;
                    return 10;
                }
            }
            """);
        try {
            Object instance = getBean(context, "introductiontest.MyBean");
            Object interceptor = getBean(context, "introductiontest.StubIntroduction");

            int result = (Integer) invoke(instance, "test");

            org.junit.jupiter.api.Assertions.assertInstanceOf(Intercepted.class, instance);
            assertEquals("my-bean", invoke(instance, "getName"));
            assertEquals("description", invoke(instance, "getDescription"));
            assertEquals("parent", invoke(instance, "getParentName"));
            assertEquals(10, result);
            assertEquals(1, getIntField(interceptor, "invoked"));
        } finally {
            context.close();
        }
    }

    @Test
    void testApplyIntroductionAdviseWithInterceptorBinding() throws Exception {
        ApplicationContext context = buildContext("""
            package introductiontest;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @TestAnn
            interface MyBean {
                int test();
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Introduction
            @interface TestAnn {
            }

            @InterceptorBean(TestAnn.class)
            class StubIntroduction implements Interceptor {
                int invoked = 0;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked++;
                    return 10;
                }
            }
            """);
        try {
            Object instance = getBean(context, "introductiontest.MyBean");
            Object interceptor = getBean(context, "introductiontest.StubIntroduction");

            int result = (Integer) invoke(instance, "test");

            org.junit.jupiter.api.Assertions.assertInstanceOf(Intercepted.class, instance);
            assertEquals(1, getIntField(interceptor, "invoked"));
            assertEquals(10, result);
        } finally {
            context.close();
        }
    }

    @Test
    void testApplyIntroductionWithExpressionsNestedAnnotation() throws Exception {
        ApplicationContext context = buildContext("""
            package introductiontest;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import io.micronaut.core.annotation.AnnotationValue;
            import io.micronaut.inject.annotation.EvaluatedAnnotationValue;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @TestAnn(@TestAnn.Expr("#{'foo'}"))
            interface MyBean {
                String test();

                @TestAnn(@TestAnn.Expr("#{'bar'}"))
                String test2();
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Introduction
            @interface TestAnn {
                Expr[] value();

                @Retention(RUNTIME)
                @interface Expr {
                    String value();
                }
            }

            @InterceptorBean(TestAnn.class)
            class StubIntroduction implements Interceptor {
                int invoked = 0;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked++;
                    AnnotationValue<Annotation> av = context.getAnnotation(TestAnn.class).getAnnotations("value").get(0);
                    return av.stringValue().orElse("not set");
                }
            }
            """);
        try {
            Object instance = getBean(context, "introductiontest.MyBean");
            Object interceptor = getBean(context, "introductiontest.StubIntroduction");

            String result = (String) invoke(instance, "test");

            org.junit.jupiter.api.Assertions.assertInstanceOf(Intercepted.class, instance);
            assertEquals(1, getIntField(interceptor, "invoked"));
            assertEquals("foo", result);
        } finally {
            context.close();
        }
    }

    @Test
    void testApplyIntroductionWithExpressions() throws Exception {
        ApplicationContext context = buildContext("""
            package introductiontest;

            import java.lang.annotation.*;
            import io.micronaut.aop.*;
            import jakarta.inject.*;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @TestAnn("#{'foo'}")
            interface MyBean {
                String test();

                @TestAnn("#{'bar'}")
                String test2();
            }

            @Retention(RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            @Introduction
            @interface TestAnn {
                String value();
            }

            @InterceptorBean(TestAnn.class)
            class StubIntroduction implements Interceptor {
                int invoked = 0;
                @Override
                public Object intercept(InvocationContext context) {
                    invoked++;
                    return context.stringValue(TestAnn.class).orElse("not set");
                }
            }
            """);
        try {
            Object instance = getBean(context, "introductiontest.MyBean");
            Object interceptor = getBean(context, "introductiontest.StubIntroduction");

            String result = (String) invoke(instance, "test");

            org.junit.jupiter.api.Assertions.assertInstanceOf(Intercepted.class, instance);
            assertEquals(1, getIntField(interceptor, "invoked"));
            assertEquals("foo", result);
            assertEquals("bar", invoke(instance, "test2"));
        } finally {
            context.close();
        }
    }
}

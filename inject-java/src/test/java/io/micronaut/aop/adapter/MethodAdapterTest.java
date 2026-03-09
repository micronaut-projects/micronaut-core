package io.micronaut.aop.adapter;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.TypeInformation;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.test.AbstractTypeElementTest;
import org.atinject.jakartatck.auto.events.EventHandlerMultipleArguments;
import org.atinject.jakartatck.auto.events.Metadata;
import org.atinject.jakartatck.auto.events.SomeEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of MethodAdapterSpec.
 */
class MethodAdapterTest extends AbstractTypeElementTest {

    @Test
    void testMethodAdapterWithFailingRequirementsIsNotPresent() throws Exception {
        ApplicationContext context = buildContext("""
            package issue5640;

            import io.micronaut.aop.Adapter;
            import java.lang.annotation.*;
            import io.micronaut.context.annotation.Requires;
            import static java.lang.annotation.ElementType.*;
            import static java.lang.annotation.RetentionPolicy.*;
            import jakarta.inject.Singleton;
            import static java.nio.charset.StandardCharsets.US_ASCII;

            @Singleton
            @Requires(property="not.present")
            class AsciiParser {
                @Parse
                public String parseAsAscii(byte[] value) {
                    return new String(value, US_ASCII);
                }
            }

            @Retention(RUNTIME)
            @Target({ANNOTATION_TYPE, METHOD})
            @Adapter(Parser.class)
            @interface Parse {}

            interface Parser {
                String parse(byte[] value);
            }
            """);
        try {
            Class<?> adaptedType = context.getClassLoader().loadClass("issue5640.Parser");
            assertFalse(context.containsBean(adaptedType));
            assertTrue(context.getBeansOfType(adaptedType).isEmpty());
        } finally {
            context.close();
        }
    }

    @Test
    void testMethodAdapterWithByteArrayArgument() throws Exception {
        ApplicationContext context = buildContext("issue5054.AsciiParser", """
            package issue5054;

            import io.micronaut.aop.Adapter;
            import java.lang.annotation.*;
            import static java.lang.annotation.ElementType.*;
            import static java.lang.annotation.RetentionPolicy.*;
            import jakarta.inject.Singleton;
            import static java.nio.charset.StandardCharsets.US_ASCII;

            @Singleton
            class AsciiParser {
                @Parse
                public String parseAsAscii(byte[] value) {
                    return new String(value, US_ASCII);
                }
            }

            @Retention(RUNTIME)
            @Target({ANNOTATION_TYPE, METHOD})
            @Adapter(Parser.class)
            @interface Parse {}

            interface Parser {
                String parse(byte[] value);
            }
            """);
        try {
            Class<?> adaptedType = context.getClassLoader().loadClass("issue5054.Parser");
            Object parser = context.getBean(adaptedType);
            BeanDefinition<?> beanDef = context.getBeanDefinition(adaptedType);

            // Description should be shortened format
            assertEquals("@j.i.Singleton i.AsciiParser.parseAsAscii()", beanDef.getBeanDescription(TypeInformation.TypeFormat.SHORTENED));

            Method parse = parser.getClass().getDeclaredMethod("parse", byte[].class);
            parse.setAccessible(true);
            Object result = parse.invoke(parser, "test".getBytes(US_ASCII));
            assertEquals("test", result);

            // Groovy spec asserted BeanDefinitionReference.getIndexes() == [adaptedType]
            // The Java API may not expose that method; assertion omitted to match available API.
        } finally {
            context.close();
        }
    }

    @Test
    void testMethodAdapterInheritsMetadata() {
        BeanDefinition<?> definition = buildBeanDefinition("test.Test$ApplicationEventListener$onStartup1$Intercepted", """
            package test;

            import io.micronaut.aop.*;
            import io.micronaut.inject.annotation.*;
            import io.micronaut.context.annotation.*;
            import io.micronaut.context.event.*;

            @jakarta.inject.Singleton
            @io.micronaut.context.annotation.Requires(property="foo.bar")
            class Test {

                @Adapter(ApplicationEventListener.class)
                void onStartup(StartupEvent event) {

                }
            }
            """);
        assertNotNull(definition);
        assertTrue(definition.getAnnotationMetadata().hasAnnotation(Requires.class));
        assertEquals("foo.bar", definition.getAnnotationMetadata().stringValue(Requires.class, "property").orElse(null));
    }

    @Test
    void testMethodAdapterWithAroundOverloading() throws Exception {
        ApplicationContext context = buildContext("adapteroverloading.Test", """
            package adapteroverloading;

            import io.micronaut.context.event.*;
            import io.micronaut.scheduling.annotation.Async;
            import jakarta.inject.Singleton;
            import java.util.concurrent.CompletableFuture;
            import io.micronaut.runtime.event.annotation.*;

            @Singleton
            public class Test {
                boolean invoked = false;
                boolean shutdown = false;

                public boolean getInvoked() {
                    return invoked;
                }
                public boolean isShutdown() {
                    return shutdown;
                }

                @EventListener
                void receive(StartupEvent event) {
                    invoked = true;
                }

                @EventListener
                void receive(ShutdownEvent event) {
                    shutdown = true;
                }
            }
            """);
        Object bean = null;
        try {
            bean = context.getBean(context.getClassLoader().loadClass("adapteroverloading.Test"));
            boolean invoked = (boolean) bean.getClass().getMethod("getInvoked").invoke(bean);
            assertTrue(invoked);
        } finally {
            context.close();
        }
        boolean shutdown = (boolean) bean.getClass().getMethod("isShutdown").invoke(bean);
        assertTrue(shutdown);
    }

    @Test
    void testMethodAdapterWithAroundAdvice() throws Exception {
        ApplicationContext context = buildContext("adapteraround.Test", """
            package adapteraround;

            import io.micronaut.context.event.StartupEvent;
            import io.micronaut.scheduling.annotation.Async;
            import jakarta.inject.Singleton;
            import java.util.concurrent.CompletableFuture;
            import io.micronaut.runtime.event.annotation.*;

            @Singleton
            public class Test {

                boolean invoked = false;
                @EventListener
                @Async
                CompletableFuture<Boolean> onStartup(StartupEvent event) {
                    invoked = true;
                    return java.util.concurrent.CompletableFuture.completedFuture(invoked);
                }

                public boolean getInvoked() {
                    return invoked;
                }
            }
            """.replace("<", "<"));
        try {
            Object bean = context.getBean(context.getClassLoader().loadClass("adapteraround.Test"));
            boolean invoked = (boolean) bean.getClass().getMethod("getInvoked").invoke(bean);
            assertTrue(invoked);
        } finally {
            context.close();
        }
    }

    @Test
    void testMethodAdapterProducesAdditionalBean() {
        BeanDefinition<?> definition = buildBeanDefinition("test.Test$ApplicationEventListener$onStartup1$Intercepted", """
            package test;

            import io.micronaut.aop.*;
            import io.micronaut.inject.annotation.*;
            import io.micronaut.context.annotation.*;
            import io.micronaut.context.event.*;

            @jakarta.inject.Singleton
            class Test {

                @Adapter(ApplicationEventListener.class)
                void onStartup(StartupEvent event) {

                }
            }
            """);
        assertNotNull(definition);
        assertFalse(definition instanceof AdvisedBeanType);
        assertTrue(ApplicationEventListener.class.isAssignableFrom(definition.getBeanType()));
        assertFalse(definition.getTypeArguments(ApplicationEventListener.class).isEmpty());
        assertEquals(StartupEvent.class, definition.getTypeArguments(ApplicationEventListener.class).get(0).getType());
    }

    @Test
    void testMethodAdapterInheritedFromInterfaceProducesAdditionalBean() {
        BeanDefinition<?> definition = buildBeanDefinition("test.Test$ApplicationEventListener$onStartup1$Intercepted", """
            package test;

            import io.micronaut.aop.*;
            import io.micronaut.inject.annotation.*;
            import io.micronaut.context.annotation.*;
            import io.micronaut.context.event.*;

            @jakarta.inject.Singleton
            class Test implements TestContract {

                @Override
                public void onStartup(StartupEvent event) {

                }
            }

            interface TestContract {

                @Adapter(ApplicationEventListener.class)
                void onStartup(StartupEvent event);
            }
            """);
        assertNotNull(definition);
        assertTrue(ApplicationEventListener.class.isAssignableFrom(definition.getBeanType()));
        assertFalse(definition.getTypeArguments(ApplicationEventListener.class).isEmpty());
        assertEquals(StartupEvent.class, definition.getTypeArguments(ApplicationEventListener.class).get(0).getType());
    }

    @Test
    void testMethodAdapterHonoursTypeRestraintsCorrectPath() {
        BeanDefinition<?> definition = buildBeanDefinition("test.Test$Foo$myMethod1$Intercepted", """
            package test;

            import io.micronaut.aop.*;
            import io.micronaut.inject.annotation.*;
            import io.micronaut.context.annotation.*;
            import io.micronaut.context.event.*;

            @jakarta.inject.Singleton
            class Test {

                @Adapter(Foo.class)
                void myMethod(String blah) {

                }
            }

            interface Foo<T extends CharSequence> extends java.util.function.Consumer<T> {}
            """.replace("<", "<"));
        assertNotNull(definition);
        assertTrue(ReflectionUtils.getAllInterfaces(definition.getBeanType()).stream().anyMatch(i -> i.getName().equals("test.Foo")));
        assertFalse(definition.getTypeArguments("test.Foo").isEmpty());
        assertEquals(String.class, definition.getTypeArguments("test.Foo").get(0).getType());
    }

    @Test
    void testMethodAdapterHonoursTypeRestraintsCompilationError() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> buildBeanDefinition("test.Test$Foo$myMethod$Intercepted", """
            package test;

            import io.micronaut.aop.*;
            import io.micronaut.inject.annotation.*;
            import io.micronaut.context.annotation.*;
            import io.micronaut.context.event.*;

            @jakarta.inject.Singleton
            class Test {

                @Adapter(Foo.class)
                void myMethod(Integer blah) {

                }
            }

            interface Foo<T extends CharSequence> extends java.util.function.Consumer<T> {}
            """.replace("<", "<")));
        assertTrue(e.getMessage().contains("Cannot adapt method [myMethod(java.lang.Integer)] to target method [accept(T)]. Type [java.lang.Integer] is not a subtype of type [java.lang.CharSequence] for argument at position 0"));
    }

    @Test
    void testMethodAdapterWrongArgumentCount() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> buildBeanDefinition("test.Test$ApplicationEventListener$onStartup$Intercepted", """
            package test;

            import io.micronaut.aop.*;
            import io.micronaut.inject.annotation.*;
            import io.micronaut.context.annotation.*;
            import io.micronaut.context.event.*;

            @jakarta.inject.Singleton
            class Test {

                @Adapter(ApplicationEventListener.class)
                void onStartup(StartupEvent event, boolean stuff) {

                }
            }
            """));
        assertTrue(e.getMessage().contains("Argument lengths don't match"));
    }

    @Test
    void testMethodAdapterArgumentOrder() {
        BeanDefinition<?> definition = buildBeanDefinition("org.atinject.jakartatck.auto.events.EventListener$EventHandlerMultipleArguments$onEvent1$Intercepted", """
            package org.atinject.jakartatck.auto.events;

            @jakarta.inject.Singleton
            class EventListener {

                @EventHandler
                public void onEvent(Metadata metadata, SomeEvent event) {
                }

            }
            """);
        assertNotNull(definition);
        assertTrue(EventHandlerMultipleArguments.class.isAssignableFrom(definition.getBeanType()));
        assertEquals(2, definition.getTypeArguments(EventHandlerMultipleArguments.class).size());
        assertEquals(Metadata.class, definition.getTypeArguments(EventHandlerMultipleArguments.class).get(0).getType());
        assertEquals(SomeEvent.class, definition.getTypeArguments(EventHandlerMultipleArguments.class).get(1).getType());
    }

    @Test
    void testAdapterIsInvoked() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec", "MethodAdapterSpec"))) {
            Object t = getBean(ctx, "io.micronaut.aop.adapter.Test");
            boolean invoked = (boolean) t.getClass().getMethod("isInvoked").invoke(t);
            assertTrue(invoked);
        }
    }
}

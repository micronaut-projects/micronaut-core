package io.micronaut.aop.named2;

import io.micronaut.aop.Intercepted;
import io.micronaut.aop.Logged;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.context.scope.Refreshable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedAopAdviceTest {


    @Test
    void testNamedBeansWithAopAdviceLookupTargetNamedBean_primaryIncluded() {
        // Setup properties to trigger @EachProperty beans for aop.test.named
        Map<String, Object> properties = Map.of(
            "aop.test.named.default", 0,
            "aop.test.named.one", 1,
            "aop.test.named.two", 2
        );
        try (ApplicationContext context = ApplicationContext.run(properties)) {
            Class<NamedInterface> namedInterfaceClass = NamedInterface.class;

            Object defaultBean = context.getBean(namedInterfaceClass);
            org.junit.jupiter.api.Assertions.assertInstanceOf(Intercepted.class, defaultBean);
            assertEquals("default", context.getBean(namedInterfaceClass).doStuff());
            assertEquals("one", context.getBean(namedInterfaceClass, Qualifiers.byName("one")).doStuff());
            assertEquals("two", context.getBean(namedInterfaceClass, Qualifiers.byName("two")).doStuff());
            assertEquals(3, context.getBeansOfType(namedInterfaceClass).size());
            assertTrue(context.getBeansOfType(namedInterfaceClass).stream().allMatch(Intercepted.class::isInstance));
        }
    }

    // Fixtures equivalent to those defined inline in the Groovy spec buildContext snippet

    interface OtherInterface {
        String doStuff();
    }

    interface NamedInterface {
        String doStuff();
    }

    @Singleton
    static class OtherBean {
        @Inject
        @Named("first")
        public OtherInterface first;

        @Inject
        @Named("second")
        public OtherInterface second;
    }

    @ConfigurationProperties("config")
    static class Config {
        public Config(Inner inner) {
        }

        @ConfigurationProperties("inner")
        public static class Inner {
        }
    }

    @Factory
    static class NamedFactory {

        @EachProperty(value = "aop.test.named", primary = "default")
        @Refreshable
        NamedInterface namedInterface(@Parameter String name) {
            return () -> name;
        }

        @Named("first")
        @Logged
        @Singleton
        OtherInterface first() {
            return () -> "first";
        }

        @Named("second")
        @Logged
        @Singleton
        OtherInterface second() {
            return () -> "second";
        }

        @EachProperty("other.interfaces")
        OtherInterface third(Config config, @Parameter String name) {
            return () -> name;
        }
    }
}

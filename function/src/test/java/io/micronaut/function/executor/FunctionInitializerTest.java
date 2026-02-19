package io.micronaut.function.executor;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class FunctionInitializerTest {

    @Test
    void fnInitializerWithProperties() {
        try (ApplicationContext ctx = ApplicationContext.builder()
            .properties(Map.of("spec.name" , "FunctionInitializerTest"))
            .build()) {

            try (FunctionInitializer fn = new FooFunctionInitializer(ctx)) {
                assertDoesNotThrow(() -> fn.getApplicationContext().getBean(Foo.class));
                assertDoesNotThrow(() -> fn.getApplicationContext().getBean(Bar.class));
            }
        }
    }

    static class FooFunctionInitializer extends FunctionInitializer {
        public FooFunctionInitializer(ApplicationContext ctx) {
            super(ctx);
            startThis(applicationContext);
        }
    }

    @Singleton
    static class Foo {
    }

    @Requires(property = "spec.name" , value = "FunctionInitializerTest")
    @Singleton
    static class Bar {
    }
}

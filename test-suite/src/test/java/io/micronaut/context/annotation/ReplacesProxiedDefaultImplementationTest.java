package io.micronaut.context.annotation;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.scheduling.annotation.Async;
import jakarta.inject.Singleton;

import java.util.Map;

class ReplacesProxiedDefaultImplementationTest {
    @Test
    void replacingAnInterfaceWithDefaultImplementationWorksWhenDefaultBeanIsProxied() {
        Map<String, Object> config = Map.of("spec.name", "ReplacesProxiedDefaultImplementationTest");
        try (ApplicationContext ctx = ApplicationContext.run(config)) {
            assertInstanceOf(GreeterReplacement.class, ctx.getBean(Greeter.class));
        }
    }

    @DefaultImplementation(DefaultGreeter.class)
    public interface Greeter {
        String greet(String name);
    }

    @Requires(property = "spec.name", value = "ReplacesProxiedDefaultImplementationTest")
    @Singleton
    static class DefaultGreeter implements Greeter {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }

        @Async // any AOP advice: this makes the bean definition an intercepted proxy
        public void warmUp() {
        }
    }

    @Requires(property = "spec.name", value = "ReplacesProxiedDefaultImplementationTest")
    @Singleton
    @Replaces(Greeter.class)
    static class GreeterReplacement implements Greeter {
        @Override
        public String greet(String name) {
            return "Hola " + name;
        }
    }
}

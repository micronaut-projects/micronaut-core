package io.micronaut.context.processor;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EagerInitExecutableMethodProcessorTest {

    @Test
    void toolAnnotationsTestWithoutEagerInitialization() {
        Map<String, Object> config = Map.of(
            "spec.name", "EagerInitExecutableMethodProcessor");
        ApplicationContext ctx = assertDoesNotThrow(() ->
            ApplicationContext.builder(config)
                .start()
        );
        assertTrue(ctx.getBean(ProcessorConsumer.class).processed);
        ctx.close();
    }

    @Test
    void toolAnnotationsTestWithEagerInitialization() {
        Map<String, Object> config = Map.of(
            "spec.name", "EagerInitExecutableMethodProcessor");
        ApplicationContext ctx = assertDoesNotThrow(() ->
            ApplicationContext.builder(config)
                .eagerInitSingletons(true)
                .start()
        );
        assertTrue(ctx.getBean(ProcessorConsumer.class).processed);
        ctx.close();
    }

    @Requires(property = "spec.name", value = "EagerInitExecutableMethodProcessor")
    @Singleton
    static class FooPrimitiveAnnotatedSingleton {
        @FooPrimitive
        public void foo() {
        }
    }

    @Requires(property = "spec.name", value = "EagerInitExecutableMethodProcessor")
    @Singleton
    static class ProcessorConsumer {
        public boolean processed;

        public ProcessorConsumer(FooExecutableMethodProcessor processor) {
            this.processed = processor.processed;
        }
    }

    @Requires(property = "spec.name", value = "EagerInitExecutableMethodProcessor")
    @Singleton
    static class FooExecutableMethodProcessor implements ExecutableMethodProcessor<FooPrimitive> {
        public boolean processed;

        @Override
        public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
            processed = true;
        }
    }

    @Requires(property = "spec.name", value = "EagerInitExecutableMethodProcessor")
    @Target(ElementType.METHOD)
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Executable(processOnStartup = true)
    public @interface FooPrimitive {
    }
}

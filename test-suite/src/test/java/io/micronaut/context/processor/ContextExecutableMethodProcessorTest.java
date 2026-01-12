package io.micronaut.context.processor;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Parallel;
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

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextExecutableMethodProcessorTest {

    @Test
    void toolAnnotationsTestWithoutEagerInitialization() {
        Map<String, Object> config = Map.of(
            "spec.name", "ContextExecutableMethodProcessorTest");
        ApplicationContext ctx = assertDoesNotThrow(() ->
            ApplicationContext.builder(config)
                .start()
        );
        ProcessorConsumer bean = ctx.getBean(ProcessorConsumer.class);
        assertTrue(bean.processed);
        assertEquals(bean.contextDependency1, bean.processor.contextDependency1);
        assertEquals(bean.contextDependency1, ctx.getBean(ContextDependency1.class));
        assertEquals(bean.contextDependency2, bean.processor.contextDependency2);
        assertEquals(bean.contextDependency2, ctx.getBean(ContextDependency2.class));
        assertEquals(bean.parallelDependency1, bean.processor.parallelDependency1);
        assertEquals(bean.parallelDependency1, ctx.getBean(ParallelDependency1.class));
        assertEquals(bean.parallelDependency2, bean.processor.parallelDependency2);
        assertEquals(bean.parallelDependency2, ctx.getBean(ParallelDependency2.class));
        ctx.close();
    }

    @Test
    void toolAnnotationsTestWithEagerInitialization() {
        Map<String, Object> config = Map.of(
            "spec.name", "ContextExecutableMethodProcessorTest");
        ApplicationContext ctx = assertDoesNotThrow(() ->
            ApplicationContext.builder(config)
                .eagerInitSingletons(true)
                .start()
        );
        ProcessorConsumer bean = ctx.getBean(ProcessorConsumer.class);
        assertTrue(bean.processed);
        assertEquals(bean.contextDependency1, bean.processor.contextDependency1);
        assertEquals(bean.contextDependency1, ctx.getBean(ContextDependency1.class));
        assertEquals(bean.parallelDependency1, bean.processor.parallelDependency1);
        assertEquals(bean.parallelDependency1, ctx.getBean(ParallelDependency1.class));
        assertEquals(bean.parallelDependency2, bean.processor.parallelDependency2);
        assertEquals(bean.parallelDependency2, ctx.getBean(ParallelDependency2.class));
        ctx.close();
    }

    @Requires(property = "spec.name", value = "ContextExecutableMethodProcessorTest")
    @MyDefaultScopeContext
    static class ContextDependency1 {
    }

    @Requires(property = "spec.name", value = "ContextExecutableMethodProcessorTest")
    @Context
    static class ContextDependency2 {
    }

    @Requires(property = "spec.name", value = "ContextExecutableMethodProcessorTest")
    @MyDefaultScopeParallel
    static class ParallelDependency1 {
    }

    @Requires(property = "spec.name", value = "ContextExecutableMethodProcessorTest")
    @Parallel
    @Singleton
    static class ParallelDependency2 {
    }


    @Requires(property = "spec.name", value = "ContextExecutableMethodProcessorTest")
    @Singleton
    static class FooPrimitiveAnnotatedSingleton {
        @FooPrimitive
        public void foo() {
        }
    }

    @Requires(property = "spec.name", value = "ContextExecutableMethodProcessorTest")
    @Singleton
    static class ProcessorConsumer {
        public boolean processed;
        public final ContextDependency1 contextDependency1;
        public final ContextDependency2 contextDependency2;
        public final ParallelDependency1 parallelDependency1;
        public final ParallelDependency2 parallelDependency2;
        public final FooExecutableMethodProcessor processor;

        public ProcessorConsumer(FooExecutableMethodProcessor processor, ContextDependency1 contextDependency, ContextDependency2 contextDependency2, ParallelDependency1 parallelDependency1, ParallelDependency2 parallelDependency2) {
            this.processed = processor.processed;
            this.contextDependency1 = contextDependency;
            this.processor = processor;
            this.contextDependency2 = contextDependency2;
            this.parallelDependency1 = parallelDependency1;
            this.parallelDependency2 = parallelDependency2;
        }
    }

    @Requires(property = "spec.name", value = "ContextExecutableMethodProcessorTest")
    @Singleton
    static class FooExecutableMethodProcessor implements ExecutableMethodProcessor<FooPrimitive> {
        public boolean processed;
        public final ContextDependency1 contextDependency1;
        public final ContextDependency2 contextDependency2;
        public final ParallelDependency1 parallelDependency1;
        public final ParallelDependency2 parallelDependency2;

        FooExecutableMethodProcessor(ContextDependency1 contextDependency, ContextDependency2 contextDependency2, ParallelDependency1 parallelDependency1, ParallelDependency2 parallelDependency2) {
            this.contextDependency1 = contextDependency;
            this.contextDependency2 = contextDependency2;
            this.parallelDependency1 = parallelDependency1;
            this.parallelDependency2 = parallelDependency2;
        }

        @Override
        public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
            processed = true;
        }
    }

    @Requires(property = "spec.name", value = "ContextExecutableMethodProcessorTest")
    @Target(ElementType.METHOD)
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Executable(processOnStartup = true)
    public @interface FooPrimitive {
    }

    @Documented
    @Retention(RUNTIME)
    @Target(TYPE)
    @Bean
    @DefaultScope(Context.class)
    public @interface MyDefaultScopeContext {
    }

    @Documented
    @Retention(RUNTIME)
    @Target(TYPE)
    @Bean
    @DefaultScope(Singleton.class)
    @Parallel
    public @interface MyDefaultScopeParallel {
    }
}

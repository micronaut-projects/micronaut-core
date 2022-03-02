package io.micronaut.context

import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Parallel
import io.micronaut.context.annotation.Requires
import io.micronaut.context.processor.ExecutableMethodProcessor
import io.micronaut.core.convert.ConversionService
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.runtime.context.scope.refresh.RefreshInterceptor
import io.micronaut.runtime.context.scope.refresh.RefreshScope
import io.micronaut.runtime.converters.time.TimeConverterRegistrar
import io.micronaut.scheduling.TaskExceptionHandler
import io.micronaut.scheduling.executor.DefaultThreadFactory
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

class AnnotationProcessorListenerSpec extends Specification {

    void 'test shutdown order of ExecutableMethodProcessor and (non-parallel) beans with executable methods'() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'spec.name': 'AnnotationProcessorListenerSpec',
                'spec.variant': 'non-parallel'
        )
        Recorder recorder = context.getBean(Recorder)
        RenderMethodProcessor processor = context.getBean(RenderMethodProcessor)
        NonParallelRenderTask task = context.getBean(NonParallelRenderTask)

        when: 'the bean context is closed'
        context.close()

        then: 'ExecutableMethodProcessor is closed before the task'
        recorder.closeOrder == [processor, task]
    }

    void 'test shutdown order of ExecutableMethodProcessor and (parallel) beans with executable methods'() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'spec.name': 'AnnotationProcessorListenerSpec',
                'spec.variant': 'parallel'
        )

        Recorder recorder = context.getBean(Recorder)
        RenderMethodProcessor processor = context.getBean(RenderMethodProcessor)
        ParallelRenderTask task = context.getBean(ParallelRenderTask)

        when: 'the bean context is closed'
        context.close()

        then: 'the ExecutableMethodProcessor is closed before the task'
        recorder.closeOrder == [processor, task]
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'AnnotationProcessorListenerSpec')
    static class Recorder {
        List closeOrder = []

        void closed(Object instance) {
            closeOrder << instance
        }
    }

    @Singleton
    @Renderer
    @Requires(property = 'spec.name', value = 'AnnotationProcessorListenerSpec')
    @Requires(property = 'spec.variant', value = 'non-parallel')
    static class NonParallelRenderTask implements Closeable {
        private Recorder recorder

        // Inject some needless beans to inflate the bean's required component count. This affects the initial ordering
        // of beans in DefaultBeanContext.topologicalSort such that, without considering the runtime required components,
        // the task typically would be closed before the ExecutableMethodProcessor. When runtime required components are
        // considered, the ExecutableMethodProcessor will be closed (appropriately) before the task.
        NonParallelRenderTask(Recorder recorder,
                              BeanContext beanContext,
                              BeanResolutionContext beanResolutionContext,
                              Optional<ConversionService<?>> conversionService,
                              TaskExceptionHandler<?, ?> taskExceptionHandler,
                              RefreshInterceptor refreshInterceptor,
                              RefreshScope refreshScope,
                              TimeConverterRegistrar timeConverterRegistrar,
                              DefaultThreadFactory threadFactory
        ) {
            this.recorder = recorder
        }

        // Need at least one method (other than constructor/close) to process.
        void render() { }

        @Override
        @PreDestroy
        void close() throws IOException {
            recorder.closed(this)
        }
    }

    @Singleton
    @Parallel
    @Renderer
    @Requires(property = 'spec.name', value = 'AnnotationProcessorListenerSpec')
    @Requires(property = 'spec.variant', value = 'parallel')
    static class ParallelRenderTask implements Closeable {
        private Recorder recorder

        // Inject some needless beans to inflate the bean's required component count. This affects the initial ordering
        // of beans in DefaultBeanContext.topologicalSort such that, without considering the runtime required components,
        // the task typically would be closed before the ExecutableMethodProcessor. When runtime required components are
        // considered, the ExecutableMethodProcessor will be closed (appropriately) before the task.
        ParallelRenderTask(Recorder recorder,
                           BeanContext beanContext,
                           BeanResolutionContext beanResolutionContext,
                           Optional<ConversionService<?>> conversionService,
                           TaskExceptionHandler<?, ?> taskExceptionHandler,
                           RefreshInterceptor refreshInterceptor,
                           RefreshScope refreshScope,
                           TimeConverterRegistrar timeConverterRegistrar,
                           DefaultThreadFactory threadFactory
        ) {
            this.recorder = recorder
        }

        // Need at least one method (other than constructor/close) to process.
        void render() { }

        @Override
        @PreDestroy
        void close() throws IOException {
            recorder.closed(this)
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'AnnotationProcessorListenerSpec')
    static class RenderMethodProcessor implements ExecutableMethodProcessor<Renderer>, Closeable {
        private static final Logger LOG = LoggerFactory.getLogger(DefaultBeanContext.class)
        private Recorder recorder

        RenderMethodProcessor(Recorder recorder) {
            this.recorder = recorder
        }

        @Override
        void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("RenderMethodProcessor.process: ${beanDefinition} - ${method}")
            }
        }

        @Override
        @PreDestroy
        void close() throws IOException {
            recorder.closed(this)
        }
    }

}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE])
@Executable(processOnStartup = true)
@interface Renderer { }

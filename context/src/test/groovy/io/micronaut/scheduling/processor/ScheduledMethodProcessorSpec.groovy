package io.micronaut.scheduling.processor

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.BeanResolutionContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.ConversionService
import io.micronaut.runtime.context.scope.refresh.RefreshInterceptor
import io.micronaut.runtime.context.scope.refresh.RefreshScope
import io.micronaut.runtime.converters.time.TimeConverterRegistrar
import io.micronaut.scheduling.TaskExceptionHandler
import io.micronaut.scheduling.annotation.Scheduled
import io.micronaut.scheduling.executor.DefaultThreadFactory
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import spock.lang.Specification

class ScheduledMethodProcessorSpec extends Specification {

    void 'test shutdown order of ScheduledMethodProcessor and beans with @Scheduled methods'() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'spec.name': 'ScheduledMethodProcessorSpec',
        )
        Recorder recorder = context.getBean(Recorder)
        ScheduledMethodProcessor processor = context.getBean(ScheduledMethodProcessor)

        when: 'the bean context is closed'
        context.close()

        then: 'the ExecutableMethodProcessor is closed before the tasks'
        recorder.closeOrder.size() == 3     // two tasks, one processor
        recorder.closeOrder.first() == processor
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ScheduledMethodProcessorSpec')
    static class Recorder {
        List closeOrder = []

        void closed(Object instance) {
            closeOrder << instance
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ScheduledMethodProcessorSpec')
    static class AlphaTask implements Closeable {
        private Recorder recorder
        private int count = 0

        // Inject some needless beans to inflate the bean's required component count. This affects the initial ordering
        // of beans in DefaultBeanContext.topologicalSort such that, without considering the runtime required components,
        // the task typically would be closed before the ExecutableMethodProcessor. When runtime required components are
        // considered, the ExecutableMethodProcessor will be closed (appropriately) before the task.
        AlphaTask(Recorder recorder,
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

        @Scheduled(fixedRate = '1m')
        void run() {
            count += 1
        }

        @Override
        @PreDestroy
        void close() throws IOException {
            recorder.closed(this)
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ScheduledMethodProcessorSpec')
    static class BetaTask implements Closeable {
        private Recorder recorder
        private int count = 0

        // Contrary to AlphaTask above, this bean would likely be closed before the ExecutableMethodProcessor even
        // without tracking runtime required components. Keeping it here to ensure behavior remains the same even after
        // the changes needed to support the case above.
        BetaTask(Recorder recorder) {
            this.recorder = recorder
        }

        @Scheduled(fixedRate = '1m')
        void run() {
            count += 1
        }

        @Override
        @PreDestroy
        void close() throws IOException {
            recorder.closed(this)
        }
    }

    // Replacing the default ScheduledMethodProcessor with a subclass in order to override close().
    @Singleton
    @Requires(property = 'spec.name', value = 'ScheduledMethodProcessorSpec')
    @Replaces(ScheduledMethodProcessor)
    static class MyScheduledMethodProcessor extends ScheduledMethodProcessor {
        private Recorder recorder

        MyScheduledMethodProcessor(
                BeanContext beanContext,
                Optional<ConversionService<?>> conversionService,
                TaskExceptionHandler<?, ?> taskExceptionHandler,
                Recorder recorder
        ) {
            super(beanContext, conversionService, taskExceptionHandler)
            this.recorder = recorder
        }

        @Override
        @PreDestroy
        void close() {
            super.close()
            recorder.closed(this)
        }
    }
}

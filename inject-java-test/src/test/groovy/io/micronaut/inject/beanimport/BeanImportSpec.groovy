package io.micronaut.inject.beanimport

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.smallrye.faulttolerance.CircuitBreakerMaintenanceImpl
import io.smallrye.faulttolerance.DefaultAsyncExecutorProvider
import io.smallrye.faulttolerance.DefaultExistingCircuitBreakerNames
import io.smallrye.faulttolerance.DefaultFallbackHandlerProvider
import io.smallrye.faulttolerance.DefaultFaultToleranceOperationProvider
import io.smallrye.faulttolerance.ExecutorHolder

class BeanImportSpec extends AbstractTypeElementSpec {

    void 'test bean import for package'() {
        given:
        ApplicationContext context = buildContext('''
package beanimporttest1;

@io.micronaut.context.annotation.Import(packages="io.smallrye.faulttolerance")
class Application {}
''')

        expect:
        context.containsBean(DefaultAsyncExecutorProvider)
        context.containsBean(CircuitBreakerMaintenanceImpl)
        context.containsBean(DefaultExistingCircuitBreakerNames)
        context.containsBean(DefaultFallbackHandlerProvider)
        context.getBeanDefinition(DefaultFallbackHandlerProvider)
                .injectedFields.size() == 1
        context.getBeanDefinition(DefaultFallbackHandlerProvider)
                .injectedFields.first().requiresReflection()
        context.containsBean(DefaultFaultToleranceOperationProvider)
        context.getBeanDefinition(DefaultFaultToleranceOperationProvider)
                .getConstructor().arguments.length == 1
        context.containsBean(ExecutorHolder)
        context.getBeanDefinition(ExecutorHolder)
                .preDestroyMethods.size() == 1
        !context.getBeanDefinition(ExecutorHolder)
                .preDestroyMethods.first().requiresReflection()
        cleanup:
        context.close()
    }

    void 'test bean import for classes'() {
        given:
        ApplicationContext context = buildContext('''
package beanimporttest1;

import io.smallrye.faulttolerance.*;
@io.micronaut.context.annotation.Import(classes={
    DefaultAsyncExecutorProvider.class,
    CircuitBreakerMaintenanceImpl.class,
    DefaultExistingCircuitBreakerNames.class,
    DefaultFallbackHandlerProvider.class,
    DefaultFaultToleranceOperationProvider.class,
    ExecutorHolder.class,
})
class Application {}
''')

        expect:
        context.containsBean(DefaultAsyncExecutorProvider)
        context.containsBean(CircuitBreakerMaintenanceImpl)
        context.containsBean(DefaultExistingCircuitBreakerNames)
        context.containsBean(DefaultFallbackHandlerProvider)
        context.getBeanDefinition(DefaultFallbackHandlerProvider)
                .injectedFields.size() == 1
        context.getBeanDefinition(DefaultFallbackHandlerProvider)
                .injectedFields.first().requiresReflection()
        context.containsBean(DefaultFaultToleranceOperationProvider)
        context.getBeanDefinition(DefaultFaultToleranceOperationProvider)
                .getConstructor().arguments.length == 1
        context.containsBean(ExecutorHolder)
        context.getBeanDefinition(ExecutorHolder)
                .preDestroyMethods.size() == 1
        !context.getBeanDefinition(ExecutorHolder)
                .preDestroyMethods.first().requiresReflection()
        cleanup:
        context.close()
    }
}

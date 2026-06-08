package io.micronaut.inject.beanimport

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.smallrye.faulttolerance.CircuitBreakerMaintenanceImpl
import io.smallrye.faulttolerance.DefaultAsyncExecutorProvider
import io.smallrye.faulttolerance.DefaultExistingCircuitBreakerNames
import io.smallrye.faulttolerance.DefaultFallbackHandlerProvider
import io.smallrye.faulttolerance.DefaultFaultToleranceOperationProvider
import io.smallrye.faulttolerance.ExecutorHolder
import jakarta.inject.Named

import java.nio.charset.StandardCharsets

class BeanImportSpec extends AbstractTypeElementSpec {

    void "test bean import with primitive array constructor"() {
        given:
        ApplicationContext context = buildContext('''
package beanimporttest1;

import io.micronaut.context.annotation.*;
import jakarta.inject.Named;
import java.nio.charset.StandardCharsets;

@Import(classes=io.micronaut.inject.beanimport.UpstreamByteConstructorBean.class)
class Application {}

@Factory
class BytesFactory {
    @Bean
    @Named("some-bytes")
    byte[] myBytes() {
        return "test".getBytes(StandardCharsets.UTF_8);
    }
}
''')
        def bean = context.getBean(UpstreamByteConstructorBean)

        expect:
        bean.toString() == 'test'
    }


    void 'test bean import preserves generic interface injection'() {
        given:
        ApplicationContext context = buildContext('''
package beanimporttest2;

import io.micronaut.context.annotation.Import;
import io.micronaut.inject.beanimport.fixtures.GenericInterface;
import io.micronaut.inject.beanimport.fixtures.ImportedGenericBean;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Import(classes = ImportedGenericBean.class)
class Application {}

@Singleton
class GenericConsumer {
    @Inject
    GenericInterface<Object> genericInterface;
}
''')

        when:
        Class<?> consumerType = context.classLoader.loadClass('beanimporttest2.GenericConsumer')
        def consumer = context.getBean(consumerType)

        then:
        consumer.genericInterface.getClass().simpleName == 'ImportedGenericBean'
        context.getBeanDefinition(io.micronaut.inject.beanimport.fixtures.ImportedGenericBean)
                .getTypeArguments(io.micronaut.inject.beanimport.fixtures.GenericInterface)[0].type == Object

        cleanup:
        context.close()
    }

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
        context.containsBean(DefaultFaultToleranceOperationProvider)
        context.getBeanDefinition(DefaultFaultToleranceOperationProvider)
                .getConstructor().arguments.length == 1
        context.containsBean(ExecutorHolder)
        context.getBeanDefinition(ExecutorHolder)
                .preDestroyMethods.size() == 1
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
        context.containsBean(DefaultFaultToleranceOperationProvider)
        context.getBeanDefinition(DefaultFaultToleranceOperationProvider)
                .getConstructor().arguments.length == 1
        context.containsBean(ExecutorHolder)
        context.getBeanDefinition(ExecutorHolder)
                .preDestroyMethods.size() == 1
        cleanup:
        context.close()
    }
}
class UpstreamByteConstructorBean {

    private final byte[] bytes;

    public UpstreamByteConstructorBean(@Named("some-bytes") byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public String toString() {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

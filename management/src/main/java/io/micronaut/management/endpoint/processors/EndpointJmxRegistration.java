package io.micronaut.management.endpoint.processors;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.management.endpoint.Endpoint;

import javax.inject.Singleton;
import javax.management.MBeanServer;

@Requires(beans = MBeanServer.class)
@Singleton
public class EndpointJmxRegistration implements ExecutableMethodProcessor<Endpoint> {

    private final MBeanServer mBeanServer;

    public EndpointJmxRegistration(MBeanServer mBeanServer) {
        this.mBeanServer = mBeanServer;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        //TODO: register the bean
    }
}

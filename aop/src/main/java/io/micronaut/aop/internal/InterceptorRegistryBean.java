package io.micronaut.aop.internal;

import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.chain.DefaultInterceptorRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.BeanFactory;

/**
 * Registers the {@link InterceptorRegistry} instance.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
public final class InterceptorRegistryBean implements BeanDefinition<InterceptorRegistry>, BeanFactory<InterceptorRegistry>, BeanDefinitionReference<InterceptorRegistry> {
    @Override
    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
        return true;
    }

    @Override
    public Class<InterceptorRegistry> getBeanType() {
        return InterceptorRegistry.class;
    }

    @Override
    public String getBeanDefinitionName() {
        return InterceptorRegistryBean.class.getName();
    }

    @Override
    public BeanDefinition<InterceptorRegistry> load() {
        return this;
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public boolean isAbstract() {
        return false;
    }

    @Override
    public InterceptorRegistry build(BeanResolutionContext resolutionContext, BeanContext context, BeanDefinition<InterceptorRegistry> definition) throws BeanInstantiationException {
        return new DefaultInterceptorRegistry(context);
    }
}

package io.micronaut.aop.internal;

import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.chain.DefaultInterceptorRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.BeanFactory;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;

import java.util.Collections;

/**
 * Registers the {@link InterceptorRegistry} instance.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
public final class InterceptorRegistryBean implements BeanDefinition<InterceptorRegistry>, BeanFactory<InterceptorRegistry>, BeanDefinitionReference<InterceptorRegistry> {
    public static final AnnotationMetadata ANNOTATION_METADATA;

    static {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(BootstrapContextCompatible.class.getName(), Collections.emptyMap());
        ANNOTATION_METADATA = metadata;
    }

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

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return ANNOTATION_METADATA;
    }
}

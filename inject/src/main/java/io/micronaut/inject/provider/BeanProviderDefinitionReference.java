package io.micronaut.inject.provider;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;

/**
 * Reference for the {@link BeanProvider} factory.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
public final class BeanProviderDefinitionReference implements BeanDefinitionReference<BeanProvider<Object>> {
    @Override
    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
        return true;
    }

    @Override
    public String getBeanDefinitionName() {
        return BeanProviderDefinition.class.getName();
    }

    @Override
    public BeanDefinition<BeanProvider<Object>> load() {
        return new BeanProviderDefinition();
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public Class<BeanProvider<Object>> getBeanType() {
        return (Class) BeanProvider.class;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return AbstractProviderDefinition.ANNOTATION_METADATA;
    }
}

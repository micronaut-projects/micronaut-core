package io.micronaut.inject.provider;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import jakarta.inject.Provider;

/**
 * Reference for the Jakarta Provider factory.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
public final class JakartaProviderBeanDefinitionReference implements BeanDefinitionReference<Provider<Object>> {
    @Override
    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
        return isPresent();
    }

    @Override
    public String getBeanDefinitionName() {
        return JavaxProviderBeanDefinition.class.getName();
    }

    @Override
    public BeanDefinition<Provider<Object>> load() {
        return new JakartaProviderBeanDefinition();
    }

    @Override
    public boolean isPresent() {
        return isTypePresent();
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    @Override
    public boolean isPrimary() {
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<Provider<Object>> getBeanType() {
        return (Class) Provider.class;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return AbstractProviderDefinition.ANNOTATION_METADATA;
    }

    static boolean isTypePresent() {
        try {
            return Provider.class.isInterface();
        } catch (Throwable e) {
            // class not present
            return false;
        }
    }
}

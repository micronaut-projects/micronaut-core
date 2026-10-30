package io.micronaut.inject.provider.exposedtypes;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.provider.AbstractInjectionPointBeanDefinition;
import org.jspecify.annotations.Nullable;

/**
 * A disposable definition that is not registered with any context, given its annotation metadata, for disposing of
 * beans handed to it directly.
 */
public final class AnyObjectDefinition extends AbstractInjectionPointBeanDefinition.Disposable<Object> {

    public AnyObjectDefinition(AnnotationMetadata annotationMetadata) {
        super(annotationMetadata);
    }

    @Override
    public Class<Object> getBeanType() {
        return Object.class;
    }

    @Override
    protected Object build(BeanResolutionContext resolutionContext,
                           BeanContext context,
                           @Nullable InjectionPoint<?> injectionPoint) {
        return new Object();
    }
}

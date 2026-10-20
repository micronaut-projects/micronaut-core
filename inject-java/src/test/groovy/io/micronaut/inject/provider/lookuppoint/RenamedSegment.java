package io.micronaut.inject.provider.lookuppoint;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.InjectionPoint;
import org.jspecify.annotations.Nullable;

/**
 * A segment of the caller's own, as a lookup narrows the point it was injected at: the segment the lookup was
 * resolved at, under another name, and its own injection point.
 *
 * @param <B> The declaring type
 * @param <T> The injected type
 */
public final class RenamedSegment<B, T> implements BeanResolutionContext.Segment<B, T>, InjectionPoint<B>, ArgumentCoercible<T> {
    private final BeanResolutionContext.Segment<B, T> delegate;
    private final String name;

    public RenamedSegment(BeanResolutionContext.Segment<B, T> delegate, String name) {
        this.delegate = delegate;
        this.name = name;
    }

    @Override
    public BeanDefinition<B> getDeclaringType() {
        return delegate.getDeclaringType();
    }

    @Override
    public @Nullable Qualifier<B> getDeclaringTypeQualifier() {
        return delegate.getDeclaringTypeQualifier();
    }

    @Override
    public InjectionPoint<B> getInjectionPoint() {
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Argument<T> getArgument() {
        return delegate.getArgument();
    }

    @Override
    public BeanDefinition<B> getDeclaringBean() {
        return delegate.getDeclaringType();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return delegate.getInjectionPoint().getAnnotationMetadata();
    }

    @Override
    public Argument<T> asArgument() {
        return delegate.getArgument();
    }

    @Override
    public String toString() {
        return delegate.getDeclaringType().getBeanType().getSimpleName() + "." + name;
    }
}

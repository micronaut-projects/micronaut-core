package io.micronaut.core.beans;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;

import java.util.Objects;

/**
 * Abstract implementation of the {@link BeanConstructor} interface.
 *
 * @param <T> The bean type
 * @author graemerocher
 * @since 3.0.0
 */
@UsedByGeneratedCode
public abstract class AbstractBeanConstructor<T> implements BeanConstructor<T> {
    private final Class<T> beanType;
    private final AnnotationMetadata annotationMetadata;
    private final Argument<?>[] arguments;

    /**
     * Default constructor.
     * @param beanType The bean type
     * @param annotationMetadata The annotation metadata
     * @param arguments The arguments
     */
    protected AbstractBeanConstructor(
            Class<T> beanType,
            AnnotationMetadata annotationMetadata,
            Argument<?>... arguments) {
        this.beanType = Objects.requireNonNull(beanType, "Bean type should not be null");
        this.annotationMetadata = annotationMetadata == null ? AnnotationMetadata.EMPTY_METADATA : annotationMetadata;
        this.arguments = ArrayUtils.isEmpty(arguments) ? Argument.ZERO_ARGUMENTS : arguments;
    }

    @Override
    @NonNull
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    @NonNull
    public Class<T> getDeclaringBeanType() {
        return beanType;
    }

    @Override
    @NonNull
    public Argument<?>[] getArguments() {
        return arguments;
    }
}

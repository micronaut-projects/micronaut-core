package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Indexed;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import javax.validation.ClockProvider;
import javax.validation.Constraint;
import java.lang.annotation.Annotation;

/**
 * Constraint validator that can be used at either runtime or compilation time and
 * is capable of validation {@link javax.validation.Constraint} instances. Allows defining validators that work with both Hibernate validator and Micronaut's validator.
 *
 * <p>Unlike the specification's interface this one can uses as a functional interface. Implementor should not implement the {@link #initialize(Annotation)} method and should instead read the passed {@link AnnotationValue}.</p>
 *
 * @param <A> The annotation type
 * @param <T> The supported validation types
 */
@Immutable
@ThreadSafe
@Indexed(ConstraintValidator.class)
@FunctionalInterface
public interface ConstraintValidator<A extends Annotation, T> extends javax.validation.ConstraintValidator<A, T> {

    /**
     * Implements the validation logic.
     *
     * <p>Implementations should be thread-safe and immutable.</p>
     *
     * @param value object to validate
     * @param annotationMetadata The annotation metadata
     * @param context The context object
     *
     * @return {@code false} if {@code value} does not pass the constraint
     */
    boolean isValid(
            @Nullable T value,
            @Nonnull AnnotationValue<A> annotationMetadata,
            @Nonnull ConstraintValidatorContext context);

    @Override
    default boolean isValid(T value, javax.validation.ConstraintValidatorContext context) {
        // simply adapt the interfaces for now.
        return isValid(value, new AnnotationValue(Constraint.class.getName()), new ConstraintValidatorContext() {
            @Nonnull
            @Override
            public ClockProvider getClockProvider() {
                return context.getClockProvider();
            }
        });
    }
}

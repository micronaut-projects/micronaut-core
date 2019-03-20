package io.micronaut.validation.validator.constraints;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import javax.validation.ValidationException;
import java.lang.annotation.Annotation;

/**
 * Default constraint validator registry.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
@Requires(missingBeans = ConstraintValidatorRegistry.class)
public class DefaultConstraintValidatorRegistry implements ConstraintValidatorRegistry {

    private final BeanContext beanContext;

    /**
     * Default constructor.
     *
     * @param beanContext The bean context
     */
    protected DefaultConstraintValidatorRegistry(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nonnull
    <A extends Annotation, T> ConstraintValidator<A, T> find(@Nonnull Class<A> constraintType, @Nonnull Class<T> targetType) {
        ArgumentUtils.requireNonNull("constraintType", constraintType);
        ArgumentUtils.requireNonNull("targetType", targetType);
        return beanContext
                .findBean(ConstraintValidator.class, Qualifiers.byTypeArguments(constraintType, targetType))
                .orElseThrow(() -> new ValidationException("No constraint validator present able to validate constraint [" + constraintType + "] on type: " + targetType));
    }

}

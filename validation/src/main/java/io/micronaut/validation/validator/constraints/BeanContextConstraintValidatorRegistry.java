package io.micronaut.validation.validator.constraints;

import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.Optional;

/**
 * Default constraint validator registry.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
@Requires(missingBeans = ConstraintValidatorRegistry.class)
@Primary
public class BeanContextConstraintValidatorRegistry implements ConstraintValidatorRegistry {

    private final BeanContext beanContext;

    /**
     * Default constructor.
     *
     * @param beanContext The bean context
     */
    protected BeanContextConstraintValidatorRegistry(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <A extends Annotation, T> Optional<ConstraintValidator<A, T>> findConstraintValidator(@Nonnull Class<A> constraintType, @Nonnull Class<T> targetType) {
        ArgumentUtils.requireNonNull("constraintType", constraintType);
        ArgumentUtils.requireNonNull("targetType", targetType);
        final Qualifier<ConstraintValidator> qualifier = Qualifiers.byTypeArguments(
                constraintType,
                ReflectionUtils.getWrapperType(targetType)
        );
        final Optional bean = beanContext
                .findBean(ConstraintValidator.class, qualifier);
        return (Optional<ConstraintValidator<A, T>>) bean;
    }

}

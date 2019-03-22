package io.micronaut.validation.validator.constraints;

import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Default hard coded implementation. Mainly for compilation time usage.
 *
 * @author graemerocher
 * @since 1.1
 */
@Internal
class DefaultValidatorConstraintRegistry implements ConstraintValidatorRegistry {

    static final ConstraintValidatorRegistry INSTANCE = new DefaultValidatorConstraintRegistry();

    final Collection<ValidatorBeanType> constraintValidators = new ArrayList<>();

    DefaultValidatorConstraintRegistry() {
//        constraintValidators.add(new ValidatorBeanType(new NotBlankConstraintValidator()));
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <A extends Annotation, T> Optional<ConstraintValidator<A, T>> findConstraintValidator(@Nonnull Class<A> constraintType, @Nonnull Class<T> targetType) {
        final Qualifier q = Qualifiers.byTypeArguments(constraintType, targetType);
        final Stream stream = constraintValidators.stream();
        return q.reduce(ConstraintValidator.class, stream).findFirst();
    }

    class ValidatorBeanType<T extends ConstraintValidator> implements BeanType<T> {
        final T t;

        ValidatorBeanType(T t) {
            this.t = t;
        }

        @Override
        public boolean isPrimary() {
            return true;
        }

        @Override
        public Class<T> getBeanType() {
            return (Class<T>) t.getClass();
        }

        @Override
        public boolean isEnabled(BeanContext context) {
            return true;
        }
    }
}

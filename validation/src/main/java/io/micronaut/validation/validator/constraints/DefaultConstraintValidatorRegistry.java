package io.micronaut.validation.validator.constraints;

import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Objects;
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
public class DefaultConstraintValidatorRegistry implements ConstraintValidatorRegistry {

    private final Map<Key, ConstraintValidator> validatorMap = new ConcurrentLinkedHashMap.Builder<Key, ConstraintValidator>().initialCapacity(10).maximumWeightedCapacity(40).build();
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
    @Nonnull
    @Override
    public <A extends Annotation, T> Optional<ConstraintValidator<A, T>> findConstraintValidator(@Nonnull Class<A> constraintType, @Nonnull Class<T> targetType) {
        ArgumentUtils.requireNonNull("constraintType", constraintType);
        ArgumentUtils.requireNonNull("targetType", targetType);
        final Key key = new Key(constraintType, targetType);
        final ConstraintValidator constraintValidator = validatorMap.get(key);
        if (constraintValidator != null) {
            if (constraintValidator != ConstraintValidator.VALID) {
                return Optional.of(constraintValidator);
            }
            return Optional.empty();
        } else {

            final Qualifier<ConstraintValidator> qualifier = Qualifiers.byTypeArguments(
                    constraintType,
                    ReflectionUtils.getWrapperType(targetType)
            );
            final ConstraintValidator cv = beanContext
                    .findBean(ConstraintValidator.class, qualifier).orElse(ConstraintValidator.VALID);
            validatorMap.put(key, cv);
            if (cv != ConstraintValidator.VALID) {
                return Optional.of(cv);
            }
            return Optional.empty();
        }
    }

    /**
     * Key for caching validators.
     * @param <A> The annotation type
     * @param <T> The target type.
     */
    private final class Key<A extends Annotation, T> {
        final Class<A> constraintType;
        final Class<T> targetType;

        Key(@Nonnull Class<A> constraintType, @Nonnull Class<T> targetType) {
            this.constraintType = constraintType;
            this.targetType = targetType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Key<?, ?> key = (Key<?, ?>) o;
            return constraintType.equals(key.constraintType) &&
                    targetType.equals(key.targetType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(constraintType, targetType);
        }
    }

}

package io.micronaut.validation.validator;

import io.micronaut.context.BeanContext;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.util.ArgumentUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.ConstraintViolation;
import javax.validation.ValidationException;
import java.util.Collection;
import java.util.Set;

@Singleton
public class DefaultValidator implements Validator {

    private final BeanContext beanContext;


    public DefaultValidator(@Nullable BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    public DefaultValidator() {
        this.beanContext = null;
    }

    @Nonnull
    @Override
    public <T> Set<ConstraintViolation<T>> validate(@Nullable T object, @Nullable Class<?>... groups) {
        ArgumentUtils.requireNonNull("object", object);
        final BeanIntrospection<?> introspection = BeanIntrospector.SHARED.findIntrospection(object.getClass())
                .orElseThrow(() -> new ValidationException("Passed object cannot be introspected. Please annotation with @Introspected"));
        final Collection<? extends BeanProperty<?, Object>> beanProperties = introspection.getBeanProperties();
        return null;
    }

    @Nonnull
    @Override
    public <T> Set<ConstraintViolation<T>> validateProperty(@Nullable T object, @Nonnull String propertyName, @Nullable Class<?>... groups) {
        return null;
    }

    @Nonnull
    @Override
    public <T> Set<ConstraintViolation<T>> validateValue(@Nonnull Class<T> beanType, @Nonnull String propertyName, @Nullable Object value, @Nullable Class<?>... groups) {
        return null;
    }
}

package io.micronaut.jackson.validation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.jackson.JacksonDeserializationPreInstantiateCallback;
import io.micronaut.validation.validator.Validator;
import jakarta.inject.Singleton;

@Requires(bean = Validator.class)
@Singleton
public class ValidationConstruction implements JacksonDeserializationPreInstantiateCallback {

    private final Validator validator;

    public ValidationConstruction(Validator validator) {
        this.validator = validator;
    }

    @Override
    public void preInstantiate(BeanIntrospection<?> beanIntrospection, Object... arguments) {
        validator.forExecutables().validateConstructorParameters(beanIntrospection, arguments);
    }
}

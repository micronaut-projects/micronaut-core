/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jackson.validation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.jackson.JacksonDeserializationPreInstantiateCallback;
import io.micronaut.validation.validator.Validator;
import jakarta.inject.Singleton;

/**
 * Validation support for bean initialized by Jackson.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
@Requires(bean = Validator.class, classes = Validator.class)
@Singleton
final class ValidationJacksonDeserializationPreInstantiateCallback implements JacksonDeserializationPreInstantiateCallback {

    private final Validator validator;

    ValidationJacksonDeserializationPreInstantiateCallback(Validator validator) {
        this.validator = validator;
    }

    @Override
    public void preInstantiate(BeanIntrospection<?> beanIntrospection, Object... arguments) {
        validator.forExecutables().validateConstructorParameters(beanIntrospection, arguments);
    }
}

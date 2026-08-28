/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.annotation.processing.test;

import io.micronaut.context.env.PropertyExpressionResolver;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.value.PropertyResolver;

import java.util.Optional;

public final class TestPropertyExpressionResolver implements PropertyExpressionResolver {

    @Override
    public @NonNull <T> Optional<T> resolve(
        @NonNull PropertyResolver propertyResolver,
        @NonNull ConversionService conversionService,
        @NonNull String expression,
        @NonNull Class<T> requiredType) {
        if ("python.service.loaded".equals(expression)) {
            return conversionService.convert("loaded", requiredType);
        }
        return Optional.empty();
    }
}

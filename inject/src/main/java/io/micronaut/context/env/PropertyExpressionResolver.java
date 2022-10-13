/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.context.env;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.value.PropertyResolver;

import java.util.Optional;

/**
 * The property expression resolver.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@FunctionalInterface
@Experimental
public interface PropertyExpressionResolver {

    /**
     * Resolve the value for the expression of the specified type.
     *
     * @param propertyResolver  The property resolver
     * @param conversionService The conversion service
     * @param expression        The expression
     * @param requiredType      The required typ
     * @param <T>               The type
     * @return The optional resolved value
     */
    @NonNull
    <T> Optional<T> resolve(@NonNull PropertyResolver propertyResolver,
                            @NonNull ConversionService conversionService,
                            @NonNull String expression,
                            @NonNull Class<T> requiredType);

}

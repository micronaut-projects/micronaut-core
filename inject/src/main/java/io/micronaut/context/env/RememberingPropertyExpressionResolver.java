/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.value.PropertyResolver;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * A {@link PropertyExpressionResolver} whose expressions resolve to another value every time, such
 * as {@code random.port}, and that remembers what an expression of a property has resolved to.
 *
 * <p>When {@link PropertySourcePropertyResolver} resolves the placeholders of a property, it passes
 * a memory for each expression it resolves: one for the expression at that place of the property's
 * text. The property then keeps the value until the property resolver forgets it, as a refresh
 * does. Where there is no property to remember the value for, the expression is resolved through
 * {@link #resolve(PropertyResolver, ConversionService, String, Class)}, which draws a new value.</p>
 *
 * @author Radovan Radic
 * @since 4.10.34
 */
@Internal
public interface RememberingPropertyExpressionResolver extends PropertyExpressionResolver {

    /**
     * Resolve the value for the expression of the specified type, keeping what the expression
     * resolves to in the given memory.
     *
     * @param propertyResolver  The property resolver
     * @param conversionService The conversion service
     * @param expression        The expression
     * @param requiredType      The required type
     * @param memory            The memory of the expression
     * @param <T>               The type
     * @return The optional resolved value
     */
    <T> Optional<T> resolve(PropertyResolver propertyResolver,
                            ConversionService conversionService,
                            String expression,
                            Class<T> requiredType,
                            Memory memory);

    /**
     * Where an expression of a property keeps what it has resolved to.
     */
    @FunctionalInterface
    interface Memory {

        /**
         * Returns what is remembered for the expression, resolving and remembering it first if
         * nothing is.
         *
         * @param value Resolves the expression, called only if nothing is remembered for it
         * @return What is remembered for the expression
         */
        Object remember(Supplier<Object> value);
    }
}

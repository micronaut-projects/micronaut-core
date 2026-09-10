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
package io.micronaut.inject.processing;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;

/**
 * Validates that a type can be proxied by a build time generated subclass or implementation.
 *
 * <p>Every proxy writer generates a type that extends or implements the proxied one, so a type that cannot be
 * extended cannot carry advice. The checks live here rather than at each proxy creation path so that the paths
 * stay consistent with each other.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Internal
public final class ProxyableTypeValidator {

    private ProxyableTypeValidator() {
    }

    /**
     * Validates that the given type can be proxied, failing with a message that names the type.
     *
     * @param type         The type to be proxied
     * @param errorElement The element the failure is reported against
     * @throws ProcessingException if the type cannot be extended by a generated proxy
     */
    public static void validateProxyable(ClassElement type, Element errorElement) {
        if (type.isFinal()) {
            throw new ProcessingException(errorElement, "Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + type.getName());
        }
        if (type.isSealed()) {
            // A sealed type permits only the subclasses it lists, which a generated proxy can never be
            throw new ProcessingException(errorElement, "Cannot apply AOP advice to sealed type. Type must be made non-sealed to support proxying: " + type.getName());
        }
    }
}

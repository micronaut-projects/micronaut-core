/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.type;

import io.micronaut.core.annotation.AnnotationMetadataProvider;

/**
 * <p>Represents an executable reference. The reference could be implemented via reflection (slow) or via generated
 * code</p>.
 *
 * @param <T> The declaring type
 * @param <R> The result of the method call
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Executable<T, R> extends AnnotationMetadataProvider {

    /**
     * The required argument types.
     *
     * @return The arguments
     */
    Argument[] getArguments();

    /**
     * Invokes the method.
     *
     * @param instance  The instance
     * @param arguments The arguments
     * @return The result
     */
    R invoke(T instance, Object... arguments);
}

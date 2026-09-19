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
package io.micronaut.context.python.runtime;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * Runtime generation failed. The message names the class, the model identity and the failing operation.
 *
 * @since 5.3.0
 */
@Internal
public final class PythonMetadataGenerationException extends RuntimeException {

    /**
     * @param beanType  The class whose metadata was generated
     * @param identity  The model identity, if the model was read
     * @param operation The failing operation
     * @param cause     The cause
     */
    public PythonMetadataGenerationException(Class<?> beanType, @Nullable String identity, String operation, Throwable cause) {
        super("Cannot generate Python metadata for " + beanType.getName() + " (model " + (identity == null ? "unread" : identity)
            + ", loader " + beanType.getClassLoader() + "): " + operation + " failed: " + cause, cause);
    }
}

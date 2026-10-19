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
package io.micronaut.python.imports;

import io.micronaut.core.annotation.Experimental;

/**
 * Thrown when the curated modules cannot be resolved: a clash no rule decides, or contributions to one module
 * that disagree.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public final class PythonImportMappingException extends RuntimeException {

    /**
     * @param message The message
     */
    public PythonImportMappingException(String message) {
        super(message);
    }
}

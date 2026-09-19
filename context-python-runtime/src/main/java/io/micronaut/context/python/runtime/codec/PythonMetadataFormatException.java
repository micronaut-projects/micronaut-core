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
package io.micronaut.context.python.runtime.codec;

import org.jspecify.annotations.Nullable;

/**
 * A saved model cannot be read: it is corrupt, written by an incompatible format version, or describes another class
 * than the one it was found for. The message names the resource and the failing operation.
 *
 * @since 5.3.0
 */
public final class PythonMetadataFormatException extends RuntimeException {

    private final String resource;

    /**
     * @param resource The resource the model was read from
     * @param message  What failed
     * @param cause    The cause, if any
     */
    public PythonMetadataFormatException(String resource, String message, @Nullable Throwable cause) {
        super("Python metadata model [" + resource + "]: " + message, cause);
        this.resource = resource;
    }

    /**
     * @return The resource the model was read from
     */
    public String getResource() {
        return resource;
    }
}

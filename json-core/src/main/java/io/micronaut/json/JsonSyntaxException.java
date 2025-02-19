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
package io.micronaut.json;

import java.io.IOException;

/**
 * Exception thrown when there is a syntax error in JSON (e.g. mismatched braces).
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
public final class JsonSyntaxException extends IOException {
    /**
     * Construct a syntax exception from a framework exception (e.g. jackson JsonParseException).
     *
     * @param cause The framework exception
     */
    public JsonSyntaxException(Throwable cause) {
        // copy the message, so it's shown properly to the user
        super(cause.getMessage(), cause);
    }

    /**
     * Construct a syntax exception with just a message.
     *
     * @param message The message
     */
    public JsonSyntaxException(String message) {
        super(message);
    }
}

/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.http.server.exceptions.format;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;

import java.util.List;
import java.util.Optional;

public interface JsonErrorContext {

    /**
     * @return The request that caused the error
     */
    @NonNull
    HttpRequest<?> getRequest();

    /**
     * @return The status to be used in the response
     */
    @NonNull
    HttpStatus getResponseStatus();

    /**
     * @return The optional root cause exception
     */
    @NonNull
    Optional<Throwable> getRootCause();

    /**
     * @return The errors
     */
    @NonNull
    List<JsonError> getErrors();

    /**
     * @return True if there are errors present
     */
    default boolean hasErrors() {
        return !getErrors().isEmpty();
    }

    /**
     * Create a new context builder.
     *
     * @param request        The request
     * @param responseStatus The response status
     * @return A new context builder
     */
    @NonNull
    static Builder builder(@NonNull HttpRequest<?> request,
                           @NonNull HttpStatus responseStatus) {
        return DefaultJsonErrorContext.builder(request, responseStatus);
    }

    /**
     * A builder for a {@link JsonErrorContext}.
     *
     * @author James Kleeh
     * @since 2.4.0
     */
    interface Builder {

        /**
         * Sets the root cause of the error(s).
         *
         * @param cause The root cause
         * @return This builder instance
         */
        @NonNull
        JsonErrorContext.Builder cause(@Nullable Throwable cause);

        /**
         * Adds an error to the context for the given message.
         *
         * @param message The message
         * @return This builder instance
         */
        @NonNull
        JsonErrorContext.Builder errorMessage(@NonNull String message);

        /**
         * Adds an error to the context.
         *
         * @param error The message
         * @return This builder instance
         */
        @NonNull
        JsonErrorContext.Builder error(@NonNull JsonError error);

        /**
         * Adds errors to the context for the given messages.
         *
         * @param errors The errors
         * @return This builder instance
         */
        @NonNull
        JsonErrorContext.Builder errorMessages(@NonNull List<String> errors);

        /**
         * Adds the errors to the context.
         *
         * @param errors The errors
         * @return This builder instance
         */
        @NonNull
        JsonErrorContext.Builder errors(@NonNull List<JsonError> errors);

        /**
         * Builds the context.
         *
         * @return A new context
         */
        @NonNull
        JsonErrorContext build();
    }
}

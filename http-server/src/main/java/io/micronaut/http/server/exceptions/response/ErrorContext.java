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
package io.micronaut.http.server.exceptions.response;

import org.jspecify.annotations.Nullable;
import io.micronaut.http.HttpRequest;

import java.util.List;
import java.util.Optional;

/**
 * Contains methods to obtain {@link Error} and {@link Throwable} from an {@link HttpRequest}.
 */
public interface ErrorContext {

    /**
     * @return The request that caused the error
     */
    HttpRequest<?> getRequest();

    /**
     * @return The optional root cause exception
     */
    Optional<Throwable> getRootCause();

    /**
     * @return The errors
     */
    List<Error> getErrors();

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
     * @return A new context builder
     */
    static Builder builder(HttpRequest<?> request) {
        return DefaultErrorContext.builder(request);
    }

    /**
     * A builder for a {@link ErrorContext}.
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
        ErrorContext.Builder cause(@Nullable Throwable cause);

        /**
         * Adds an error to the context for the given message.
         *
         * @param message The message
         * @return This builder instance
         */
        ErrorContext.Builder errorMessage(String message);

        /**
         * Adds an error to the context.
         *
         * @param error The message
         * @return This builder instance
         */
        ErrorContext.Builder error(Error error);

        /**
         * Adds errors to the context for the given messages.
         *
         * @param errors The errors
         * @return This builder instance
         */
        ErrorContext.Builder errorMessages(List<String> errors);

        /**
         * Adds the errors to the context.
         *
         * @param errors The errors
         * @return This builder instance
         */
        ErrorContext.Builder errors(List<Error> errors);

        /**
         * Builds the context.
         *
         * @return A new context
         */
        ErrorContext build();
    }
}

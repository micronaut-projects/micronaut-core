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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Internal
final class DefaultJsonErrorContext implements JsonErrorContext {

    private final HttpRequest<?> request;
    private final HttpStatus responseStatus;
    private final Throwable cause;
    private final List<JsonError> jsonErrors;

    private DefaultJsonErrorContext(@NonNull HttpRequest<?> request,
                                    @NonNull HttpStatus responseStatus,
                                    @Nullable Throwable cause,
                                    @NonNull List<JsonError> jsonErrors) {
        this.request = request;
        this.responseStatus = responseStatus;
        this.cause = cause;
        this.jsonErrors = jsonErrors;
    }

    @Override
    @NonNull
    public HttpRequest<?> getRequest() {
        return request;
    }

    @Override
    @NonNull
    public HttpStatus getResponseStatus() {
        return responseStatus;
    }

    @Override
    @NonNull
    public Optional<Throwable> getRootCause() {
        return Optional.ofNullable(cause);
    }

    @Override
    @NonNull
    public List<JsonError> getErrors() {
        return jsonErrors;
    }

    /**
     * Creates a context builder for this implementation.
     *
     * @param request The request
     * @param responseStatus The response status
     * @return A new builder
     */
    public static Builder builder(@NonNull HttpRequest<?> request,
                                  @NonNull HttpStatus responseStatus) {
        return new Builder(request, responseStatus);
    }

    private static final class Builder implements JsonErrorContext.Builder {

        private final HttpRequest<?> request;
        private final HttpStatus responseStatus;
        private Throwable cause;
        private final List<JsonError> jsonErrors = new ArrayList<>();

        private Builder(@NonNull HttpRequest<?> request,
                        @NonNull HttpStatus responseStatus) {
            this.request = request;
            this.responseStatus = responseStatus;
        }

        @Override
        @NonNull
        public Builder cause(@Nullable Throwable cause) {
            this.cause = cause;
            return this;
        }

        @Override
        @NonNull
        public Builder errorMessage(@NonNull String message) {
            jsonErrors.add(() -> message);
            return this;
        }

        @Override
        @NonNull
        public Builder error(@NonNull JsonError error) {
            jsonErrors.add(error);
            return this;
        }

        @Override
        @NonNull
        public Builder errorMessages(@NonNull List<String> errors) {
            for (String error: errors) {
                errorMessage(error);
            }
            return this;
        }

        @Override
        @NonNull
        public Builder errors(@NonNull List<JsonError> errors) {
            jsonErrors.addAll(errors);
            return this;
        }

        @Override
        @NonNull
        public JsonErrorContext build() {
            return new DefaultJsonErrorContext(request, responseStatus, cause, jsonErrors);
        }
    }
}

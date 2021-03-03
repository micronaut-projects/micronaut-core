package io.micronaut.http.server.exceptions.format;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DefaultJsonErrorContext implements JsonErrorContext {

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
    public HttpRequest<?> getRequest() {
        return request;
    }

    @Override
    public HttpStatus getResponseStatus() {
        return responseStatus;
    }

    @Override
    public Optional<Throwable> getRootCause() {
        return Optional.ofNullable(cause);
    }

    @Override
    public List<JsonError> getErrors() {
        return jsonErrors;
    }

    public static Builder builder(@NonNull HttpRequest<?> request,
                                  @NonNull HttpStatus responseStatus) {
        return new Builder(request, responseStatus);
    }

    public static class Builder implements JsonErrorContext.Builder {

        private final HttpRequest<?> request;
        private final HttpStatus responseStatus;
        private Throwable cause;
        List<JsonError> jsonErrors = new ArrayList<>();

        private Builder(@NonNull HttpRequest<?> request,
                        @NonNull HttpStatus responseStatus) {
            this.request = request;
            this.responseStatus = responseStatus;
        }

        public Builder cause(@Nullable Throwable cause) {
            this.cause = cause;
            return this;
        }

        public Builder errorMessage(String message) {
            jsonErrors.add(() -> message);
            return this;
        }

        public Builder error(JsonError error) {
            jsonErrors.add(error);
            return this;
        }

        public Builder errorMessages(List<String> errors) {
            for (String error: errors) {
                errorMessage(error);
            }
            return this;
        }

        public Builder errors(List<JsonError> errors) {
            jsonErrors.addAll(errors);
            return this;
        }

        public JsonErrorContext build() {
            return new DefaultJsonErrorContext(request, responseStatus, cause, jsonErrors);
        }
    }
}

package io.micronaut.http.server.exceptions.format;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;

import java.util.List;
import java.util.Optional;

public interface JsonErrorContext {

    @NonNull
    HttpRequest<?> getRequest();

    @NonNull
    HttpStatus getResponseStatus();

    @NonNull
    Optional<Throwable> getRootCause();

    @NonNull
    List<JsonError> getErrors();

    default boolean hasErrors() {
        return !getErrors().isEmpty();
    }

    interface Builder {

        JsonErrorContext.Builder cause(@Nullable Throwable cause);

        JsonErrorContext.Builder errorMessage(String message);

        JsonErrorContext.Builder error(JsonError error);

        JsonErrorContext.Builder errorMessages(List<String> errors);

        JsonErrorContext.Builder errors(List<JsonError> errors);

        JsonErrorContext build();
    }

    static Builder builder(@NonNull HttpRequest<?> request,
                           @NonNull HttpStatus responseStatus) {
        return DefaultJsonErrorContext.builder(request, responseStatus);
    }
}

package io.micronaut.web.router.version.strategy;

import io.micronaut.http.HttpRequest;

import java.util.Optional;

/**
 * Interface responsible for extracting request version from {@link HttpRequest}.
 */
public interface VersionExtractingStrategy {

    /**
     * Extracts the version from {@link HttpRequest}.
     *
     * @param request The HTTP request
     * @return Optional of the version value or {@code Optional.empty()} if version cannot be extracted
     */
    Optional<String> extract(HttpRequest<?> request);

}

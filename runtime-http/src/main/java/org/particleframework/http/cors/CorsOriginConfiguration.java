package org.particleframework.http.cors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Stores configuration for CORS
 *
 * @author James Kleeh
 * @since 1.0
 */
public class CorsOriginConfiguration {

    public static List<String> ANY = new ArrayList<>(Arrays.asList("*"));

    Optional<List<String>> allowedOrigins = Optional.of(ANY);
    Optional<List<String>> allowedMethods = Optional.of(ANY);
    Optional<List<String>> allowedHeaders = Optional.of(ANY);
    Optional<List<String>> exposedHeaders = Optional.empty();
    Optional<Boolean> allowCredentials = Optional.of(true);
    Optional<Long> maxAge = Optional.of(1800L);


    public Optional<List<String>> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(Optional<List<String>> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public Optional<List<String>> getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(Optional<List<String>> allowedMethods) {
        this.allowedMethods = allowedMethods;
    }

    public Optional<List<String>> getAllowedHeaders() {
        return allowedHeaders;
    }

    public void setAllowedHeaders(Optional<List<String>> allowedHeaders) {
        this.allowedHeaders = allowedHeaders;
    }

    public Optional<List<String>> getExposedHeaders() {
        return exposedHeaders;
    }

    public void setExposedHeaders(Optional<List<String>> exposedHeaders) {
        this.exposedHeaders = exposedHeaders;
    }

    public Optional<Boolean> getAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(Optional<Boolean> allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public Optional<Long> getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Optional<Long> maxAge) {
        this.maxAge = maxAge;
    }
}

package io.micronaut.inject.context.retain.replaced;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * The application's credentials: a class a restart replaces.
 */
@Singleton
@Requires(property = "spec.name", value = "ReplacedClassRetentionSpec")
public class AppCredentials implements Credentials {
    @Override
    public String token() {
        return "app";
    }
}

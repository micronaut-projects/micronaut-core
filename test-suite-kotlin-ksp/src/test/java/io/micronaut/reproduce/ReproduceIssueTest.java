package io.micronaut.reproduce;

import org.junit.jupiter.api.Test;

/**
 * Trivial test whose only purpose is to ensure the test sources are compiled.
 * If HttpClientConfiguration#getConnectionPoolConfiguration() is abstract,
 * compilation will fail because TwitterHttpClientConfiguration does not
 * implement that method (matching the user-guide example).
 */
public class ReproduceIssueTest {

    @Test
    void compileOnlySmoke() {
        // Intentionally empty. Successful compilation of the test sources
        // (which include TwitterHttpClientConfiguration) indicates the
        // framework does not declare getConnectionPoolConfiguration() as abstract.
    }
}

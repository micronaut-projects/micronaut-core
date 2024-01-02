
package io.micronaut.inject.test.external;

import io.micronaut.context.annotation.Value;

public class ExternalConfiguration {
    private final String endpoint;
    private final boolean wrapped;
    private final int leaseMinRenewalSeconds;

    public ExternalConfiguration(@Value("${vault.endpoint:}") String endpoint,
                                 @Value("${vault.token.wrapped:false}") boolean wrapped,
                                 @Value("${vault.lease.renewal.minRenewalSeconds:10}") int leaseMinRenewalSeconds) {
        this.endpoint = endpoint;
        this.wrapped = wrapped;
        this.leaseMinRenewalSeconds = leaseMinRenewalSeconds;
    }
}

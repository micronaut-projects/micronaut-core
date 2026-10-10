package io.micronaut.inject.context.retain;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "RetainedRegistrationsSpec")
public class LeaseHolderA {
    public final Lease lease;

    public LeaseHolderA(Lease lease) {
        this.lease = lease;
    }
}

package io.micronaut.inject.context.retain;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;

@Prototype
@Requires(property = "spec.name", value = "RetainedRegistrationsSpec")
public class Lease {
    public final Source source;

    public Lease(Source source) {
        this.source = source;
    }
}

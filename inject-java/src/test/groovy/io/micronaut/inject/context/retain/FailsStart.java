package io.micronaut.inject.context.retain;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;

@Context
@Requires(property = "spec.name", value = "RetainedRegistrationsSpec")
@Requires(property = "retain.fail-start")
public class FailsStart {

    public FailsStart(Consumer consumer) {
        throw new IllegalStateException("this context fails to start");
    }
}

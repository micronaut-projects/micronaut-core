package io.micronaut.inject.visitor.beans;

import io.micronaut.context.annotation.Executable;

import java.time.Instant;

public abstract class Auditable {

    private Instant updatedAt;

    @Executable(processOnStartup = true)
    protected void beforeInsert() {
        updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}


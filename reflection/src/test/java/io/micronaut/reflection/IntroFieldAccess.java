package io.micronaut.reflection;

import io.micronaut.core.annotation.Introspected;

/**
 * A type asking for its fields to be its properties, which the processor admits for every field but a private
 * one.
 */
@Introspected(accessKind = Introspected.AccessKind.FIELD)
public class IntroFieldAccess {

    public String label = "label";

    String note = "note";

    private String secret = "secret"; // NOSONAR - unread on purpose, the specs assert the introspection leaves it out
}

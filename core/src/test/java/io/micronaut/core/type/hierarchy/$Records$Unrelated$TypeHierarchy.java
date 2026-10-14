package io.micronaut.core.type.hierarchy;

import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GeneratedTypeHierarchy;

import java.io.Serializable;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static io.micronaut.core.type.hierarchy.Records.of;
import static io.micronaut.core.type.hierarchy.Records.variable;

/**
 * The record of {@link Records.Unrelated}, which counts how often it was instantiated.
 */
@SuppressWarnings({"checkstyle:TypeName", "unused"})
public final class $Records$Unrelated$TypeHierarchy extends GeneratedTypeHierarchy {
    public static final AtomicInteger LOADED = new AtomicInteger();

    public $Records$Unrelated$TypeHierarchy() {
        super(Records.Unrelated.class, null, null, null);
        LOADED.incrementAndGet();
    }
}

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
 * The record of {@link Records.Payload}, which counts how often it was instantiated.
 */
@SuppressWarnings({"checkstyle:TypeName", "unused"})
public final class $Records$Payload$TypeHierarchy extends GeneratedTypeHierarchy {
    public static final AtomicInteger LOADED = new AtomicInteger();

    public $Records$Payload$TypeHierarchy() {
        super(Records.Payload.class, new Argument[]{variable("T", Number.class)},
            of(ArrayList.class, variable("T", Number.class)),
            new Argument[]{of(Supplier.class, of(List.class, variable("T", Number.class)))},
            Records.concat(Records.concat(List.of(of(ArrayList.class, variable("T", Number.class))), Records.arrayListSuperTypes(variable("T", Number.class))),
                List.of(of(Supplier.class, of(List.class, variable("T", Number.class))), Argument.OBJECT_ARGUMENT)).toArray(Argument.ZERO_ARGUMENTS));
        LOADED.incrementAndGet();
    }
}

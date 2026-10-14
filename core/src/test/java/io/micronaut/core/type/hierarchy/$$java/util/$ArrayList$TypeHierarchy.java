package io.micronaut.core.type.hierarchy.$$java.util;

import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GeneratedTypeHierarchy;

import java.io.Serializable;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.micronaut.core.type.hierarchy.Records;

import static io.micronaut.core.type.hierarchy.Records.of;
import static io.micronaut.core.type.hierarchy.Records.variable;

/**
 * The record of {@link ArrayList}, written into this package as a record of a class of the JDK is.
 */
@SuppressWarnings({"checkstyle:TypeName", "unused"})
public final class $ArrayList$TypeHierarchy extends GeneratedTypeHierarchy {
    public static final AtomicInteger LOADED = new AtomicInteger();

    public $ArrayList$TypeHierarchy() {
        super(ArrayList.class, new Argument[]{variable("E")},
            of(AbstractList.class, variable("E")),
            new Argument[]{of(List.class, variable("E")), Argument.of(RandomAccess.class), Argument.of(Cloneable.class), Argument.of(Serializable.class)},
            Records.concat(Records.arrayListSuperTypes(variable("E")), List.of(Argument.OBJECT_ARGUMENT)).toArray(Argument.ZERO_ARGUMENTS));
        LOADED.incrementAndGet();
    }
}

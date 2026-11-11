package io.micronaut.core.type.$$java.util;

import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GeneratedTypeHierarchy;

import java.util.ArrayList;

import static io.micronaut.core.type.hierarchy.Records.variable;

/**
 * A record of {@link ArrayList} written from another package than the one of the record in
 * {@code io.micronaut.core.type.hierarchy}, which left a super type out: a lookup prefers the other.
 */
@SuppressWarnings({"checkstyle:TypeName", "unused"})
public final class $ArrayList$TypeHierarchy extends GeneratedTypeHierarchy {

    public $ArrayList$TypeHierarchy() {
        super(ArrayList.class, new Argument[]{variable("E")}, null, null, null, new String[]{"java.util.AbstractList"});
    }
}

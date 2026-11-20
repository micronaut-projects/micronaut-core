package io.micronaut.inject.visitor.beans.hierarchy;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.TypeHierarchyImport;

/**
 * Leaves the value variable of {@link KeyedRepo} open.
 *
 * @param <X> The value
 */
@TypeHierarchyImport
@Introspected(hierarchy = true)
public class OpenRepo<X extends Number> implements KeyedRepo<String, X> {
    @Executable
    @Override
    public void put(String key, X value) {
        // only the declaration is read
    }

    @Executable
    @Override
    public void putAll(X[] values) {
        // only the declaration is read
    }
}

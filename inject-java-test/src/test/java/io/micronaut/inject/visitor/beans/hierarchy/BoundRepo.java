package io.micronaut.inject.visitor.beans.hierarchy;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.TypeHierarchyImport;

/**
 * Binds both variables of {@link KeyedRepo}.
 */
@TypeHierarchyImport
@Introspected(hierarchy = true)
public class BoundRepo implements KeyedRepo<String, Integer> {
    @Executable
    @Override
    public void put(String key, Integer value) {
        // only the declaration is read
    }

    @Executable
    @Override
    public void putAll(Integer[] values) {
        // only the declaration is read
    }
}

package io.micronaut.inject.visitor.beans.hierarchy;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.TypeHierarchyImport;

/**
 * A generic level whose hierarchy is recorded.
 *
 * @param <K> The key
 * @param <V> The value
 */
@TypeHierarchyImport
@Introspected(hierarchy = true)
public interface KeyedRepo<K, V> {
    @Executable
    void put(K key, V value);

    @Executable
    void putAll(V[] values);
}

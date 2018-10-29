package io.micronaut.configuration.kafka.streams.registry;

import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import javax.inject.Singleton;

@Singleton
public class QueryableStoreRegistryExample {

    private final QueryableStoreRegistry queryableStoreRegistry;

    public QueryableStoreRegistryExample(QueryableStoreRegistry queryableStoreRegistry) { // <1>
        this.queryableStoreRegistry = queryableStoreRegistry;
    }

    public <K, V> ReadOnlyKeyValueStore<K, V> getStore(String storeName) { // <2>
        return queryableStoreRegistry.getQueryableStoreType(storeName, QueryableStoreTypes.keyValueStore());
    }

    public <K, V> V getValue(String storeName, K key) { // <3>
        ReadOnlyKeyValueStore<K, V> readOnlyKeyValueStore = getStore(storeName);
        return readOnlyKeyValueStore != null ? readOnlyKeyValueStore.get(key) : null;
    }
}

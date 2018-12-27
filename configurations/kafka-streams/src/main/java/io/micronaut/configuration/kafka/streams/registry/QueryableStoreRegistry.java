/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.configuration.kafka.streams.registry;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreType;

/**
 * A factory that constructs a {@link KafkaStreamsRegistry} and {@link QueryableStoreRegistry} beans.
 *
 * @author Christian Oestreich
 * @since 1.1
 */
public class QueryableStoreRegistry {

    private final KafkaStreamsRegistry kafkaStreamsRegistry;

    /**
     * Constructor method for creating a QueryableStoreRegistry.
     *
     * @param kafkaStreamsRegistry The bean containing all the registered streams
     */
    QueryableStoreRegistry(KafkaStreamsRegistry kafkaStreamsRegistry) {
        this.kafkaStreamsRegistry = kafkaStreamsRegistry;
    }

    /**
     * Retrieve and return a queryable store by name created in the application.
     *
     * @param storeName name of the queryable store
     * @param storeType type of the queryable store
     * @param <T>       generic queryable store
     * @return queryable store.
     */
    public <T> T getQueryableStoreType(String storeName, QueryableStoreType<T> storeType) {
        for (KafkaStreams kafkaStream : this.kafkaStreamsRegistry.getKafkaStreams()) {
            try {
                T store = kafkaStream.store(storeName, storeType);
                if (store != null) {
                    return store;
                }
            } catch (InvalidStateStoreException ignored) {
                //pass through
            }
        }
        return null;
    }

}

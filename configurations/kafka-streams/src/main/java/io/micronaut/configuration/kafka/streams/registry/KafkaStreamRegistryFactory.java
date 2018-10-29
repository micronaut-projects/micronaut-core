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

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import org.apache.kafka.streams.KafkaStreams;

import java.util.Set;

/**
 * A factory that constructs a {@link KafkaStreamsRegistry} and {@link QueryableStoreRegistry} beans.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
@Factory
public class KafkaStreamRegistryFactory {

    /**
     * Exposes the {@link KafkaStreamsRegistry} as a bean.
     *
     * @param kafkaStreamsList The list of registered streams
     * @return The kafka streams registry
     */
    @Bean
    public KafkaStreamsRegistry kafkaStreamsRegistry(Set<KafkaStreams> kafkaStreamsList) {
        return new KafkaStreamsRegistry(kafkaStreamsList);
    }

    /**
     * Exposes the {@link QueryableStoreRegistry} as a bean.
     *
     * @param kafkaStreamsRegistry The bean that contains the list of registered streams
     * @return The queryable store registry
     */
    @Bean
    public QueryableStoreRegistry queryableStoreTypeRegistry(KafkaStreamsRegistry kafkaStreamsRegistry) {
        return new QueryableStoreRegistry(kafkaStreamsRegistry);
    }
}

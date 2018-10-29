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

import java.util.Set;

/**
 * A bean to hold all the registered streams for use in activities like querying state stores, etc.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
class KafkaStreamsRegistry {

    private final Set<KafkaStreams> kafkaStreams;

    /**
     * Constructor method for creating a KafkaStreamsRegistry.
     *
     * @param kafkaStreams A set of all the kafka streams
     */
    KafkaStreamsRegistry(Set<KafkaStreams> kafkaStreams) {
        this.kafkaStreams = kafkaStreams;
    }

    /**
     * Get all the registered {@link KafkaStreams} objects.
     *
     * @return set of {@link KafkaStreams} objects
     */
    Set<KafkaStreams> getKafkaStreams() {
        return kafkaStreams;
    }
}

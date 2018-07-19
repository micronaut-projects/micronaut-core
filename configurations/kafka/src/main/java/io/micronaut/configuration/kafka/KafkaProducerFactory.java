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

package io.micronaut.configuration.kafka;

import io.micronaut.configuration.kafka.config.AbstractKafkaProducerConfiguration;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.exceptions.ConfigurationException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Properties;

/**
 * A factory class for creating Kafka {@link org.apache.kafka.clients.producer.Producer} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
public class KafkaProducerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducerFactory.class);

    /**
     * Creates a new {@link KafkaProducer} for the given configuration.
     *
     * @param producerConfiguration The producer configuration
     * @param <K> The key type
     * @param <V> The value type
     * @return The consumer
     */
    @Prototype
    public <K, V> KafkaProducer<K, V> createProducer(@Parameter AbstractKafkaProducerConfiguration<K, V> producerConfiguration) {
        Optional<Serializer<K>> keySerializer = producerConfiguration.getKeySerializer();
        Optional<Serializer<V>> valueSerializer = producerConfiguration.getValueSerializer();

        Properties config = producerConfiguration.getConfig();
        if (keySerializer.isPresent() && valueSerializer.isPresent()) {
            Serializer<K> ks = keySerializer.get();
            Serializer<V> vs = valueSerializer.get();
            return new KafkaProducer<>(
                    config,
                    ks,
                    vs
            );
        } else if (keySerializer.isPresent() || valueSerializer.isPresent()) {
            throw new ConfigurationException("Both the [keySerializer] and [valueSerializer] must be set when setting either");
        } else {
            return new KafkaProducer<>(
                    config
            );
        }
    }
}

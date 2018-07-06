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

package io.micronaut.configuration.kafka.config;

import org.apache.kafka.common.serialization.Deserializer;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Properties;

/**
 * Abstract Configuration for Apache Kafka Consumer. See http://kafka.apache.org/documentation/#consumerconfigs
 *
 * @param <K> The key deserializer type
 * @param <V> The value deserializer type
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractKafkaConsumerConfiguration<K, V> extends AbstractKafkaConfiguration<K, V> {

    private Deserializer<K> keyDeserializer;
    private Deserializer<V> valueDeserializer;

    /**
     * Constructs a new instance.
     *
     * @param config The config to use
     */
    @SuppressWarnings("WeakerAccess")
    protected AbstractKafkaConsumerConfiguration(Properties config) {
        super(config);
    }

    /**
     * @return The default key {@link Deserializer}
     */
    @SuppressWarnings("WeakerAccess")
    public Optional<Deserializer<K>> getKeyDeserializer() {
        return Optional.ofNullable(keyDeserializer);
    }

    /**
     * Sets the key deserializer.
     *
     * @param keyDeserializer The key serializer
     */
    public void setKeyDeserializer(@Nullable Deserializer<K> keyDeserializer) {
        this.keyDeserializer = keyDeserializer;
    }

    /**
     * @return The default value {@link Deserializer}
     */
    @SuppressWarnings("WeakerAccess")
    public Optional<Deserializer<V>> getValueDeserializer() {
        return Optional.ofNullable(valueDeserializer);
    }

    /**
     * Sets the default value deserializer.
     *
     * @param valueDeserializer The value deserializer
     */
    public void setValueDeserializer(@Nullable Deserializer<V> valueDeserializer) {
        this.valueDeserializer = valueDeserializer;
    }
}

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

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import java.util.Properties;

/**
 * Default Kafka consumer configuration that comes into play if no other config is specified.
 *
 * @author Graeme Rocher
 * @since 1.0
 *
 * @param <K>
 * @param <V>
 */
@Requires(missingProperty = KafkaConsumerConfiguration.PREFIX + ".default")
@Primary
public class DefaultKafkaConsumerConfiguration<K, V> extends AbstractKafkaConsumerConfiguration<K, V> {
    /**
     * Construct a new {@link KafkaConsumerConfiguration} for the given defaults.
     *
     * @param defaultConfiguration The default configuration
     */
    public DefaultKafkaConsumerConfiguration(
            KafkaDefaultConfiguration defaultConfiguration) {
        super(new Properties());
        Properties config = getConfig();
        config.putAll(defaultConfiguration.getConfig());
        config.putIfAbsent(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                AbstractKafkaConsumerConfiguration.DEFAULT_KEY_DESERIALIZER);
        config.putIfAbsent(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                AbstractKafkaConsumerConfiguration.DEFAULT_VALUE_DESERIALIZER);
    }
}

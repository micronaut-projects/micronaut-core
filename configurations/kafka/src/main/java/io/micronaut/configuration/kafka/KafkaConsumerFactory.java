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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import org.apache.kafka.clients.consumer.KafkaConsumer;

/**
 * A factory class for creating Kafka {@link org.apache.kafka.clients.consumer.Consumer} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
public class KafkaConsumerFactory {

    /**
     * Creates a new {@link KafkaConsumer} for the given configuration.
     *
     * @param consumerConfiguration The consumer configuration
     * @param <K> The key type
     * @param <V> The value type
     * @return The consumer
     */
    @Prototype
    public <K, V> KafkaConsumer<K, V> createConsumer(@Parameter KafkaConsumerConfiguration consumerConfiguration) {
        return new KafkaConsumer<>(
                consumerConfiguration.getConfig()
        );
    }
}

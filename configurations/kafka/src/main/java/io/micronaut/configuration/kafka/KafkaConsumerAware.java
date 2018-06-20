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

import org.apache.kafka.clients.consumer.KafkaConsumer;

import javax.annotation.Nonnull;

/**
 * Interface for {@link io.micronaut.configuration.kafka.annotation.KafkaListener} instances to implement
 * if they wish to obtain a reference to the underlying {@link org.apache.kafka.clients.consumer.KafkaConsumer}.
 *
 * @param <K> The key type
 * @param <V> The value type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface KafkaConsumerAware<K, V> {

    /**
     * Called when the underlying {@link KafkaConsumer} is created.
     *
     * @param consumer The consumer
     */
    void setKafkaConsumer(@Nonnull KafkaConsumer<K, V> consumer);
}

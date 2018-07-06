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

package io.micronaut.configuration.kafka.annotation;

/**
 * <p>An enum representing different strategies for committing offsets to Kafka when using {@link KafkaListener}.</p>
 *
 * <p>To track records that have been consumed Kafka allows committing offsets at a frequency desired by the developer. This tracking is done by committing offsets for a given partition back to Kafka.</p>
 *
 * <p>Depending on requirements you may wish the commit more or less frequently and you may not care whether the commit was successful or not. This enum allows configuring a range of policies for a Kafka consumer from leaving it down to the Kafka client (with {code AUTO}) to synchronously committing offsets after each consumer record is consumed.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public enum OffsetStrategy {
    /**
     * Automatically commit offsets with the {@link org.apache.kafka.clients.consumer.Consumer#poll(long)} loop.
     */
    AUTO,
    /**
     * Do not commit offsets. In this case the consumer method should accept an argument that is the {@link org.apache.kafka.clients.consumer.KafkaConsumer} itself and call {@link org.apache.kafka.clients.consumer.KafkaConsumer#commitSync()}.
     */
    DISABLED,
    /**
     * Synchronously commit offsets using {@link org.apache.kafka.clients.consumer.Consumer#commitSync()} after each batch of messages is processed.
     */
    SYNC,
    /**
     * Asynchronously commit offsets using {@link org.apache.kafka.clients.consumer.Consumer#commitAsync()} after each batch of messages is processed.
     */
    ASYNC,
    /**
     * Synchronously commit offsets using {@link org.apache.kafka.clients.consumer.Consumer#commitSync()} after each {@link org.apache.kafka.clients.consumer.ConsumerRecord} is consumed.
     */
    SYNC_PER_RECORD,
    /**
     * Asynchronously commit offsets using {@link org.apache.kafka.clients.consumer.Consumer#commitSync()} after each {@link org.apache.kafka.clients.consumer.ConsumerRecord} is consumed.
     */
    ASYNC_PER_RECORD

}

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
package io.micronaut.configurations.kafka;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;

import javax.inject.Singleton;

/**
 * @author Iván López
 * @since 1.0
 */
@Factory
public class KafkaFactory {

    /**
     * Creates the Kafka Producer bean
     *
     * @param configuration The configuration for the producer
     * @return The {@link Producer} instance
     */
    @Bean(preDestroy = "close")
    @Singleton
    Producer kafkaProducer(KafkaProducerConfiguration configuration) {
        return new KafkaProducer<String, String>(configuration.getConfig());
    }

    /**
     * Creates the Kafka Consumer bean
     *
     * @param consumerConfiguration The configuration for the consumer
     * @return The {@link Consumer} instance
     */
    @Bean
    @Prototype
    Consumer kafkaConsumer(KafkaConsumerConfiguration consumerConfiguration) {
        return new KafkaConsumer(consumerConfiguration.getConfig());
    }
}

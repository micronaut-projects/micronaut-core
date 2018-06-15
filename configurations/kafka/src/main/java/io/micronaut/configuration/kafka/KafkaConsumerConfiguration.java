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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.env.Environment;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import java.util.Properties;

/**
 * Configuration for Apache Kafka Consumer. See http://kafka.apache.org/documentation/#consumerconfigs
 *
 * @author Iván López
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(KafkaConsumerConfiguration.PREFIX)
public class KafkaConsumerConfiguration {

    /**
     * The default consumers configuration.
     */
    public static final String PREFIX = "kafka.consumers";

    private static final Class DEFAULT_KEY_DESERIALIZER = org.apache.kafka.common.serialization.ByteArrayDeserializer.class;
    private static final Class DEFAULT_VALUE_DESERIALIZER = org.apache.kafka.common.serialization.StringDeserializer.class;


    private final Properties config;

    /**
     * Construct a new {@link KafkaConsumerConfiguration} for the given defaults.
     *
     * @param defaultConfiguration The default configuration
     * @param environment The environment
     */
    public KafkaConsumerConfiguration(
            KafkaDefaultConfiguration defaultConfiguration,
            Environment environment) {
        this.config = new Properties();
        this.config.putAll(defaultConfiguration.getConfig());
        this.config.putAll(environment.getProperty(PREFIX, Properties.class).orElseGet(Properties::new));

        config.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, DEFAULT_KEY_DESERIALIZER);
        config.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, DEFAULT_VALUE_DESERIALIZER);
    }

    /**
     * @return The Kafka Consumer configuration
     */
    public Properties getConfig() {
        return config;
    }

}

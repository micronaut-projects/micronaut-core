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
import org.apache.kafka.clients.producer.ProducerConfig;

import javax.annotation.Nonnull;
import java.util.Properties;

/**
 * Configuration for Apache Kafka Producer. See http://kafka.apache.org/documentation.html#producerconfigs
 *
 * @author Iván López
 * @author Graeme Rocher
 *
 * @since 1.0
 */
@ConfigurationProperties(KafkaProducerConfiguration.PREFIX)
public class KafkaProducerConfiguration {

    /**
     * The default configuration for producers.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String PREFIX = "kafka.producers";

    private static final Class DEFAULT_KEY_SERIALIZER = org.apache.kafka.common.serialization.ByteArraySerializer.class;
    private static final Class DEFAULT_VALUE_SERIALIZER = org.apache.kafka.common.serialization.StringSerializer.class;
    private final Properties config;


    /**
     * Constructs the default producer configuration.
     *
     * @param defaultConfiguration The default Kafka configuration
     * @param environment The environment
     */
    public KafkaProducerConfiguration(
            KafkaDefaultConfiguration defaultConfiguration,
            Environment environment) {
        this.config = new Properties();
        this.config.putAll(defaultConfiguration.getConfig());
        this.config.putAll(environment.getProperty(PREFIX, Properties.class).orElseGet(Properties::new));
        config.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, DEFAULT_KEY_SERIALIZER);
        config.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, DEFAULT_VALUE_SERIALIZER);

    }

    /**
     * @return The Kafka Producer configuration
     */
    @SuppressWarnings("WeakerAccess")
    public @Nonnull Properties getConfig() {
        return config;
    }

}

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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.env.Environment;
import org.apache.kafka.clients.producer.ProducerConfig;

import javax.annotation.PostConstruct;
import java.util.Properties;

/**
 * Configuration for Apache Kafka Producer. See http://kafka.apache.org/documentation.html#producerconfigs
 *
 * @author Iván López
 * @since 1.0
 */
@ConfigurationProperties(KafkaProducerConfiguration.PREFIX)
public class KafkaProducerConfiguration {

    private Properties config;

    static final String PREFIX = "kafka.producer";

    private static final Class DEFAULT_KEY_SERIALIZER = org.apache.kafka.common.serialization.ByteArraySerializer.class;
    private static final Class DEFAULT_VALUE_SERIALIZER = org.apache.kafka.common.serialization.StringSerializer.class;
    private static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";

    @PostConstruct
    void postConstruct(Environment environment) {
        config = environment.getProperty(KafkaProducerConfiguration.PREFIX, Properties.class, new Properties());

        // Mandatory values for the producer configuration
        config.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, DEFAULT_KEY_SERIALIZER);
        config.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, DEFAULT_VALUE_SERIALIZER);
        config.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, DEFAULT_BOOTSTRAP_SERVERS);
    }

    /**
     * @return The Kafka Producer configuration
     */
    public Properties getConfig() {
        return config;
    }
}

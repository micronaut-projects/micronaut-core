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

import javax.annotation.Nonnull;
import java.util.Properties;

/**
 * The default Kafka configuration to apply to both the consumer and the producer, but can be overridden by either.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(KafkaDefaultConfiguration.PREFIX)
public class KafkaDefaultConfiguration {
    /**
     * The default prefix used for Kafka configuration.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String PREFIX = "kafka";
    /**
     * The default bootstrap server address.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";

    private final Properties config;

    /**
     * Constructs the default Kafka configuration.
     *
     * @param environment The environment
     */
    public KafkaDefaultConfiguration(Environment environment) {
        this.config = environment.getProperty(PREFIX, Properties.class).orElseGet(Properties::new);
        config.putIfAbsent(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KafkaDefaultConfiguration.DEFAULT_BOOTSTRAP_SERVERS
        );
    }

    /**
     * @return The Kafka configuration
     */
    @SuppressWarnings("WeakerAccess")
    public @Nonnull Properties getConfig() {
        if (config != null) {
            return config;
        }
        return new Properties();
    }

}

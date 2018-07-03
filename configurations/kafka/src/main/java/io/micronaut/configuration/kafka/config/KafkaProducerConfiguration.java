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

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.naming.NameUtils;

import java.util.Properties;

/**
 * Configuration for Apache Kafka Producer. See http://kafka.apache.org/documentation.html#producerconfigs
 *
 * @param <K> The key deserializer type
 * @param <V> The value deserializer type
 * @author Iván López
 * @author Graeme Rocher
 *
 * @since 1.0
 */
@EachProperty(value = KafkaProducerConfiguration.PREFIX, primary = "default")
@Requires(beans = KafkaDefaultConfiguration.class)
public class KafkaProducerConfiguration<K, V> extends AbstractKafkaProducerConfiguration<K, V> {

    /**
     * The default configuration for producers.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String PREFIX = "kafka.producers";

    /**
     * Constructs the default producer configuration.
     *
     * @param producerName The name of the producer
     * @param defaultConfiguration The default Kafka configuration
     * @param environment The environment
     */
    public KafkaProducerConfiguration(
            @Parameter String producerName,
            KafkaDefaultConfiguration defaultConfiguration,
            Environment environment) {
        super(new Properties());
        Properties config = getConfig();
        config.putAll(defaultConfiguration.getConfig());
        String propertyKey = PREFIX + '.' + NameUtils.hyphenate(producerName, true);
        config.putAll(environment.getProperty(propertyKey, Properties.class).orElseGet(Properties::new));

    }

}

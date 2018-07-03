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
 * Configuration for Apache Kafka Consumer. See http://kafka.apache.org/documentation/#consumerconfigs
 *
 * @param <K> The key deserializer type
 * @param <V> The value deserializer type
 * @author Iván López
 * @author Graeme Rocher
 * @since 1.0
 */
@EachProperty(value = KafkaConsumerConfiguration.PREFIX, primary = "default")
@Requires(beans = KafkaDefaultConfiguration.class)
public class KafkaConsumerConfiguration<K, V> extends AbstractKafkaConsumerConfiguration<K, V> {

    /**
     * The default consumers configuration.
     */
    public static final String PREFIX = "kafka.consumers";

    /**
     * Construct a new {@link KafkaConsumerConfiguration} for the given defaults.
     *
     * @param consumerName The name of the consumer
     * @param defaultConfiguration The default configuration
     * @param environment The environment
     */
    public KafkaConsumerConfiguration(
            @Parameter String consumerName,
            KafkaDefaultConfiguration defaultConfiguration,
            Environment environment) {
        super(new Properties());
        Properties config = getConfig();
        config.putAll(defaultConfiguration.getConfig());
        String propertyKey = PREFIX + '.' + NameUtils.hyphenate(consumerName, true);
        config.putAll(environment.getProperty(propertyKey, Properties.class).orElseGet(Properties::new));
    }

}

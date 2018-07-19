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

package io.micronaut.configuration.kafka.streams;

import io.micronaut.configuration.kafka.config.KafkaDefaultConfiguration;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.runtime.ApplicationConfiguration;

import java.util.Properties;

import static io.micronaut.configuration.kafka.streams.KafkaStreamsConfiguration.PREFIX;

/**
 * The default configuration passed to {@link org.apache.kafka.streams.KafkaStreams}.
 *
 * @param <K> The generic key type
 * @param <V> The generic value type
 */
@EachProperty(value = PREFIX, primary = "default")
@Requires(beans = KafkaDefaultConfiguration.class)
public class KafkaStreamsConfiguration<K, V> extends AbtractKafkaStreamsConfiguration<K, V> {

    /**
     * The default streams configuration.
     */
    public static final String PREFIX = "kafka.streams";


    /**
     * Construct a new {@link KafkaStreamsConfiguration} for the given defaults.
     *
     * @param streamName The stream name
     * @param defaultConfiguration The default configuration
     * @param applicationConfiguration The application configuration
     * @param environment The environment
     */
    public KafkaStreamsConfiguration(
            @Parameter String streamName,
            KafkaDefaultConfiguration defaultConfiguration,
            ApplicationConfiguration applicationConfiguration,
            Environment environment) {
        super(defaultConfiguration);
        Properties config = getConfig();
        String propertyKey = PREFIX + '.' + NameUtils.hyphenate(streamName, true);
        config.putAll(environment.getProperty(propertyKey, Properties.class).orElseGet(Properties::new));
        init(applicationConfiguration, environment, config);
    }
}

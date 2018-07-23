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
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.ApplicationConfiguration;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Properties;

/**
 * The default streams configuration is non other is present.
 *
 * @author graemerocher
 * @since 1.0
 * @param <K>
 * @param <V>
 */
@Requires(missingProperty = KafkaStreamsConfiguration.PREFIX + ".default")
@Singleton
@Requires(beans = KafkaDefaultConfiguration.class)
@Named("default")
@Primary
public class DefaultKafkaStreamsConfiguration<K, V> extends AbtractKafkaStreamsConfiguration<K, V> {
    /**
     * Construct a new {@link KafkaStreamsConfiguration} for the given defaults.
     *
     * @param defaultConfiguration The default configuration
     * @param applicationConfiguration The application configuration
     * @param environment The environment
     */
    public DefaultKafkaStreamsConfiguration(KafkaDefaultConfiguration defaultConfiguration,
                                            ApplicationConfiguration applicationConfiguration,
                                            Environment environment) {
        super(defaultConfiguration);
        Properties config = getConfig();
        config.putAll(defaultConfiguration.getConfig());
        init(applicationConfiguration, environment, config);
    }
}

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

import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration;
import io.micronaut.configuration.kafka.config.KafkaDefaultConfiguration;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.ApplicationConfiguration;
import org.apache.kafka.streams.StreamsConfig;

import java.io.File;
import java.util.Properties;

/**
 * Abstract streams configuration.
 *
 * @author graemerocher
 * @since 1.0
 * @param <K> The key deserializer type
 * @param <V> The value deserializer type
 */
public class AbstractKafkaStreamsConfiguration<K, V> extends AbstractKafkaConfiguration<K, V> {

    /**
     * Construct a new {@link KafkaStreamsConfiguration} for the given defaults.
     *
     * @param defaultConfiguration The default configuration
     */
    protected AbstractKafkaStreamsConfiguration(KafkaDefaultConfiguration defaultConfiguration) {
        super(new Properties());
        Properties config = getConfig();
        config.putAll(defaultConfiguration.getConfig());
    }

    /**
     * Shared initialization.
     *
     * @param applicationConfiguration The application config
     * @param environment The env
     * @param config The config to be initialized
     */
    protected void init(ApplicationConfiguration applicationConfiguration, Environment environment, Properties config) {
        // set the default application id
        String applicationName = applicationConfiguration.getName().orElse(Environment.DEFAULT_NAME);
        config.putIfAbsent(StreamsConfig.APPLICATION_ID_CONFIG, applicationName);

        if (environment.getActiveNames().contains(Environment.TEST)) {
            String tmpDir = System.getProperty("java.io.tmpdir");
            if (StringUtils.isNotEmpty(tmpDir)) {
                if (new File(tmpDir, applicationName).mkdirs()) {
                    config.putIfAbsent(StreamsConfig.STATE_DIR_CONFIG, tmpDir);
                }
            }
        }
    }
}

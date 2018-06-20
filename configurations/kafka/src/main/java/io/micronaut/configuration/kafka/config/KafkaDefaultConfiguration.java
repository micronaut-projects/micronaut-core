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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.env.Environment;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import java.util.Properties;

/**
 * The default Kafka configuration to apply to both the consumer and the producer, but can be overridden by either.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(AbstractKafkaConfiguration.PREFIX)
public class KafkaDefaultConfiguration extends AbstractKafkaConfiguration {

    /**
     * Constructs the default Kafka configuration.
     *
     * @param environment The environment
     */
    public KafkaDefaultConfiguration(Environment environment) {
        super(environment.getProperty(PREFIX, Properties.class).orElseGet(Properties::new));
        getConfig().putIfAbsent(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                AbstractKafkaConfiguration.DEFAULT_BOOTSTRAP_SERVERS
        );
    }

}

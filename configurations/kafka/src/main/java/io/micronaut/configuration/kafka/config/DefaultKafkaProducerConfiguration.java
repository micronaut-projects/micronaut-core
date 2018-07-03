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

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;

import javax.inject.Inject;
import java.util.Properties;

/**
 * The default {@link org.apache.kafka.clients.producer.KafkaProducer} configuration when no default is specified.
 *
 * @param <K>
 * @param <V>
 */
@Requires(missingProperty = KafkaProducerConfiguration.PREFIX + ".default")
@Primary
@Prototype
@Requires(beans = KafkaDefaultConfiguration.class)
public class DefaultKafkaProducerConfiguration<K, V> extends AbstractKafkaProducerConfiguration<K, V> {
    /**
     * Constructs a new instance.
     *
     * @param defaultConfiguration The default Kafka configuration
     */
    @Inject
    public DefaultKafkaProducerConfiguration(KafkaDefaultConfiguration defaultConfiguration) {
        super(new Properties());
        init(defaultConfiguration);
    }


    /**
     * Construct a new {@link KafkaConsumerConfiguration} for the given defaults.
     *
     * @param defaultConfiguration The default configuration
     */
    public DefaultKafkaProducerConfiguration(
            AbstractKafkaConfiguration defaultConfiguration) {
        super(new Properties());
        init(defaultConfiguration);
    }

    private void init(AbstractKafkaConfiguration defaultConfiguration) {
        Properties config = getConfig();
        config.putAll(defaultConfiguration.getConfig());

        if (defaultConfiguration instanceof AbstractKafkaProducerConfiguration) {
            @SuppressWarnings("unchecked")
            AbstractKafkaProducerConfiguration<K, V> defaultProducerConfig = (AbstractKafkaProducerConfiguration) defaultConfiguration;

            defaultProducerConfig.getKeySerializer().ifPresent(this::setKeySerializer);
            defaultProducerConfig.getValueSerializer().ifPresent(this::setValueSerializer);
        }
    }
}

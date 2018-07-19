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

package io.micronaut.configuration.kafka.embedded;

import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Configuration for the embedded Kafka server.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(KafkaEmbeddedConfiguration.PREFIX)
public class KafkaEmbeddedConfiguration implements Toggleable {
    /**
     * The prefix used for configuration.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String PREFIX = AbstractKafkaConfiguration.PREFIX + ".embedded";

    private boolean enabled = false;
    private List<String> topics = new ArrayList<>();
    private Properties properties = new Properties();

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the embedded Kafka server is enabled.
     *
     * @param enabled True if it is.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return The broker properties.
     */
    public @Nonnull Properties getProperties() {
        return properties;
    }

    /**
     * Sets the broker properties.
     *
     * @param properties The broker properties.
     */
    public void setProperties(Properties properties) {
        if (properties != null) {
            this.properties = properties;
        }
    }

    /**
     * @return The topics that should be created by the embedded instance
     */
    public List<String> getTopics() {
        return topics;
    }

    /**
     * The topics that should be created by the embedded instance.
     * @param topics The topic names
     */
    public void setTopics(List<String> topics) {
        if (topics != null) {
            this.topics = topics;
        }
    }
}

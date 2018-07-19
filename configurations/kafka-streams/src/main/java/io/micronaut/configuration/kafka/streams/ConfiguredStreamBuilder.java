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

import org.apache.kafka.streams.StreamsBuilder;

import javax.annotation.Nonnull;
import java.util.Properties;

/**
 * Extended version of {@link StreamsBuilder} that can be configured.
 *
 * @author graemerocher
 * @since 1.0
 */
public class ConfiguredStreamBuilder extends StreamsBuilder {

    private final Properties configuration = new Properties();

    /**
     * Default constructor.
     *
     * @param configuration The configuration
     */
    public ConfiguredStreamBuilder(Properties configuration) {
        this.configuration.putAll(configuration);
    }

    /**
     * The configuration. Can be mutated.
     *
     * @return The configuration
     */
    public @Nonnull Properties getConfiguration() {
        return configuration;
    }
}

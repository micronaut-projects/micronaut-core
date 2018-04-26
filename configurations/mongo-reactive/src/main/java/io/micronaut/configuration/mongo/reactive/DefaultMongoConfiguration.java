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

package io.micronaut.configuration.mongo.reactive;

import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.ApplicationConfiguration;

/**
 * The default MongoDB configuration class.
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(property = MongoSettings.PREFIX)
@Requires(classes = MongoClientOptions.class)
@ConfigurationProperties(MongoSettings.PREFIX)
public class DefaultMongoConfiguration extends AbstractMongoConfiguration {

    @ConfigurationBuilder(prefixes = "", configurationPrefix = "options")
    protected MongoClientOptions.Builder clientOptions = MongoClientOptions.builder();

    /**
     * Constructor.
     * @param applicationConfiguration applicationConfiguration
     */
    public DefaultMongoConfiguration(ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
    }

    /**
     * @return Builds the {@link MongoClientOptions}
     */
    @Override
    public MongoClientOptions buildOptions() {
        clientOptions.applicationName(getApplicationName());
        return clientOptions.build();
    }

    /**
     * @return Builds the MongoDB URI
     */
    public MongoClientURI buildURI() {
        clientOptions.applicationName(getApplicationName());
        return new MongoClientURI(getUri(), clientOptions);
    }
}

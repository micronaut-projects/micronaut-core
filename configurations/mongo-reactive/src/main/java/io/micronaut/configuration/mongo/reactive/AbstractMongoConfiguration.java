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

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientOptions;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.ApplicationConfiguration;

import javax.validation.constraints.NotBlank;
import java.util.Optional;

/**
 * @author graemerocher
 * @since 1.0
 */
public abstract class AbstractMongoConfiguration {

    private String uri = MongoSettings.DEFAULT_URI;

    private final ApplicationConfiguration applicationConfiguration;

    /**
     * Constructor.
     * @param applicationConfiguration applicationConfiguration
     */
    public AbstractMongoConfiguration(ApplicationConfiguration applicationConfiguration) {
        this.applicationConfiguration = applicationConfiguration;
    }

    /**
     * @return The MongoDB URI
     */
    @NotBlank
    public String getUri() {
        return uri;
    }

    /**
     * Sets the MongoDB URI.
     *
     * @param uri The MongoDB URI
     */
    public void setUri(String uri) {
        if (StringUtils.isNotEmpty(uri)) {
            this.uri = uri;
        }
    }

    /**
     * @return The MongoDB {@link ConnectionString}
     */
    public Optional<ConnectionString> getConnectionString() {
        if (StringUtils.isNotEmpty(uri)) {
            return Optional.of(new ConnectionString(uri));
        }
        return Optional.empty();
    }

    /**
     * @return Builds the options
     */
    public abstract MongoClientOptions buildOptions();

    /**
     * Get the application name or return the default.
     * @return applicationName
     */
    protected String getApplicationName() {
        return applicationConfiguration.getName().orElse(Environment.DEFAULT_NAME);
    }
}

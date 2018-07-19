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

package io.micronaut.http.client;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Primary;
import io.micronaut.http.ssl.ClientSslConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * The default configuration if no explicit configuration is specified for an HTTP client.
 *
 * @author graemerocher
 * @since 1.0
 */
@ConfigurationProperties(DefaultHttpClientConfiguration.PREFIX)
@Primary
public class DefaultHttpClientConfiguration extends HttpClientConfiguration {

    /**
     * Prefix for HTTP Client settings.
     */
    public static final String PREFIX = "micronaut.http.client";

    /**
     * Default constructor.
     */
    public DefaultHttpClientConfiguration() {
    }

    /**
     * @param applicationConfiguration The application configuration
     */
    @Inject
    public DefaultHttpClientConfiguration(ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
    }

    /**
     * Uses the default SSL configuration.
     *
     * @param sslConfiguration The SSL configuration
     */
    @Inject
    public void setClientSslConfiguration(@Nullable ClientSslConfiguration sslConfiguration) {
        if (sslConfiguration != null) {
            super.setSslConfiguration(sslConfiguration);
        }
    }
}

/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.http.client.interceptor.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;

import java.util.Collections;


/**
 * Default configuration when no other is present.
 *
 * @author graemerocher
 * @since 1.1.0
 */
@ConfigurationProperties(DefaultClientVersioningConfiguration.PREFIX)
@Primary
@Requires(missingProperty = DefaultClientVersioningConfiguration.PREFIX)
@Internal
public class DefaultClientVersioningConfiguration extends ClientVersioningConfiguration {

    public static final String DEFAULT_HEADER_NAME = "X-API-VERSION";
    public static final String DEFAULT_PARAMETER_NAME = "api-version";
    public static final String PREFIX = ClientVersioningConfiguration.PREFIX + "." + DEFAULT;

    /**
     * Default constructor.
     */
    DefaultClientVersioningConfiguration() {
        super(ClientVersioningConfiguration.DEFAULT);
        setHeaders(Collections.singletonList(
            DEFAULT_HEADER_NAME
        ));
    }
}

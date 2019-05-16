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
package io.micronaut.http.server.hostresolver;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.core.util.Toggleable;

import javax.annotation.Nonnull;

/**
 * {@link HttpHeaderHostResolver} configuration.
 * @author Sergio del Amo
 * @since 1.2.0
 */
@DefaultImplementation(HttpHeaderHostResolverConfigurationProperties.class)
public interface HttpHeaderHostResolverConfiguration extends Toggleable {

    /**
     *
     * @return The HTTP Header name used to resolve Host.
     */
    @Nonnull
    String getHostHeaderName();

    /**
     *
     * @return The HTTP Header name used to resolve Protocol.
     */
    @Nonnull
    String getProtocolHeaderName();
}

/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.http.simple.SimpleHttpRequestFactory;
import io.micronaut.http.simple.SimpleHttpResponseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Internal helper class for resolving the default request factory.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
class DefaultHttpFactories {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpFactories.class);

    /**
     * Resolves the default request factory.
     *
     * @return The default request factory.
     */
    static HttpRequestFactory resolveDefaultRequestFactory() {
        Optional<ServiceDefinition<HttpRequestFactory>> definition = SoftServiceLoader.load(HttpRequestFactory.class)
                .firstOr("io.micronaut.http.client.NettyClientHttpRequestFactory", HttpRequestFactory.class.getClassLoader());

        if (definition.isPresent()) {
            ServiceDefinition<HttpRequestFactory> sd = definition.get();
            try {
                return sd.load();
            } catch (Throwable e) {
                LOG.warn("Unable to load default request factory for definition [" + definition + "]: " + e.getMessage(), e);
            }
        }
        return new SimpleHttpRequestFactory();
    }

    /**
     * Resolves the default request factory.
     *
     * @return The default request factory.
     */
    static HttpResponseFactory resolveDefaultResponseFactory() {
        Optional<ServiceDefinition<HttpResponseFactory>> definition = SoftServiceLoader.load(HttpResponseFactory.class)
                .firstOr("io.micronaut.http.server.netty.NettyHttpResponseFactory", HttpResponseFactory.class.getClassLoader());

        if (definition.isPresent()) {
            ServiceDefinition<HttpResponseFactory> sd = definition.get();
            try {
                return sd.load();
            } catch (Throwable e) {
                LOG.warn("Unable to load default response factory for definition [" + definition + "]: " + e.getMessage(), e);
            }
        }
        return new SimpleHttpResponseFactory();
    }

}

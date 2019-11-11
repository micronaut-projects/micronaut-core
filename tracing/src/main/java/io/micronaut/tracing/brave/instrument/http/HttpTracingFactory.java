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
package io.micronaut.tracing.brave.instrument.http;

import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;
import brave.http.HttpTracing;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;

/**
 * Adds HTTP tracing for Micronaut using Brave.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
@Requires(beans = Tracing.class)
@Requires(classes = HttpTracing.class)
public class HttpTracingFactory {

    /**
     * The {@link HttpTracing} bean.
     *
     * @param tracing The {@link Tracing} bean
     * @return The {@link HttpTracing} bean
     */
    @Singleton
    @Requires(missingBeans = HttpTracing.class)
    HttpTracing httpTracing(Tracing tracing) {
        return HttpTracing.create(tracing);
    }

    /**
     * The {@link HttpClientHandler} bean.
     *
     * @param httpTracing The {@link HttpTracing} bean
     * @return The {@link HttpClientHandler} bean
     */
    @Singleton
    HttpClientHandler<HttpClientRequest, HttpClientResponse> httpClientHandler(HttpTracing httpTracing) {
        return HttpClientHandler.create(httpTracing);
    }

    /**
     * The {@link HttpServerHandler} bean.
     *
     * @param httpTracing The {@link HttpTracing} bean
     * @return The {@link HttpServerHandler} bean
     */
    @Singleton
    HttpServerHandler<HttpServerRequest, HttpServerResponse> httpServerHandler(HttpTracing httpTracing) {
        return HttpServerHandler.create(httpTracing);
    }
}

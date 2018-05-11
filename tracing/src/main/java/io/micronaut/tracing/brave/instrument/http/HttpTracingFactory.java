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

package io.micronaut.tracing.brave.instrument.http;

import brave.Tracing;
import brave.http.*;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import zipkin2.Endpoint;

import javax.inject.Singleton;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

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
    @Bean
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
    @Bean
    @Singleton
    HttpClientHandler<HttpRequest<?>, HttpResponse<?>> httpClientHandler(HttpTracing httpTracing) {
        return HttpClientHandler.create(httpTracing, new HttpClientAdapter<HttpRequest<?>, HttpResponse<?>>() {
            @Override
            public String method(HttpRequest<?> request) {
                return request.getMethod().name();
            }

            @Override
            public String url(HttpRequest<?> request) {
                return request.getUri().toString();
            }

            @Override
            public String requestHeader(HttpRequest<?> request, String name) {
                return request.getHeaders().get(name);
            }

            @Override
            public Integer statusCode(HttpResponse<?> response) {
                return response.getStatus().getCode();
            }

            @Override
            public boolean parseServerAddress(HttpRequest<?> httpRequest, Endpoint.Builder builder) {
                InetAddress address = httpRequest.getServerAddress().getAddress();
                return builder.parseIp(address);
            }

            @Override
            public String methodFromResponse(HttpResponse<?> httpResponse) {
                return httpResponse.getAttribute(HttpAttributes.METHOD_NAME, String.class)
                                   .orElseGet(() -> super.methodFromResponse(httpResponse));
            }

            @Override
            public String route(HttpResponse<?> response) {
                Optional<String> value = response.getAttribute(HttpAttributes.URI_TEMPLATE, String.class);
                return value.orElseGet(() -> super.route(response));
            }
        });
    }

    /**
     * The {@link HttpServerHandler} bean.
     *
     * @param httpTracing The {@link HttpTracing} bean
     * @return The {@link HttpServerHandler} bean
     */
    @Bean
    @Singleton
    HttpServerHandler<HttpRequest<?>, HttpResponse<?>> httpServerHandler(HttpTracing httpTracing) {
        return HttpServerHandler.create(httpTracing, new HttpServerAdapter<HttpRequest<?>, HttpResponse<?>>() {
            @Override
            public String method(HttpRequest<?> request) {
                return request.getMethod().name();
            }

            @Override
            public String url(HttpRequest<?> request) {
                return request.getUri().toString();
            }

            @Override
            public String requestHeader(HttpRequest<?> request, String name) {
                return request.getHeaders().get(name);
            }

            @Override
            public Integer statusCode(HttpResponse<?> response) {
                return response.getStatus().getCode();
            }

            @Override
            public String route(HttpResponse<?> response) {
                Optional<String> value = response.getAttribute(HttpAttributes.URI_TEMPLATE, String.class);
                return value.orElseGet(() -> super.route(response));
            }

            @Override
            public String methodFromResponse(HttpResponse<?> httpResponse) {
                return httpResponse.getAttribute(HttpAttributes.METHOD_NAME, String.class).orElse(null);
            }

            @Override
            public boolean parseClientAddress(HttpRequest<?> httpRequest, Endpoint.Builder builder) {
                InetSocketAddress remoteAddress = httpRequest.getRemoteAddress();
                return builder.parseIp(remoteAddress.getAddress());
            }
        });
    }
}

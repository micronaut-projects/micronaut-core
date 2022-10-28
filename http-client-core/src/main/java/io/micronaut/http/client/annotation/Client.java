/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.annotation;

import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.hateoas.JsonError;
import jakarta.inject.Singleton;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Allows defining declarative HTTP clients and customizing injection for injecting {@link io.micronaut.http.client.HttpClient} implementations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Introduction
@Singleton
// tag::value[]
public @interface Client {

    /**
     * @return The URL or service ID of the remote service
     */
    @AliasFor(member = "id") // <1>
    String value() default "";

    /**
     * @return The ID of the client
     */
    @AliasFor(member = "value") // <2>
    String id() default "";
// end::value[]

    /**
     * The base URI for the client. Only to be used in
     * conjunction with {@link #id()}.
     *
     * @return The base URI
     */
    String path() default "";

    /**
     * @return The type used to decode errors
     */
    Class<?> errorType() default JsonError.class;

    /**
     * @return The http client configuration bean to use
     */
    Class<? extends HttpClientConfiguration> configuration() default HttpClientConfiguration.class;

    /**
     * The HTTP version.
     *
     * @return The HTTP version of the client.
     * @deprecated There are now separate settings for HTTP and HTTPS connections. To configure
     * HTTP connections (e.g. for h2c), use {@link #plaintextMode}. To configure ALPN, set
     * {@link #alpnModes}.
     */
    @Deprecated
    HttpVersion httpVersion() default HttpVersion.HTTP_1_1;

    /**
     * The connection mode to use for <i>plaintext</i> (http as opposed to https) connections.
     * <br>
     * <b>Note: If {@link #httpVersion} is set, this setting is ignored!</b>
     *
     * @return The plaintext connection mode.
     * @since 4.0.0
     */
    @NonNull
    HttpVersionSelection.PlaintextMode plaintextMode() default HttpVersionSelection.PlaintextMode.HTTP_1;

    /**
     * The protocols to support for TLS ALPN. If HTTP 2 is included, this will also restrict the
     * TLS cipher suites to those supported by the HTTP 2 standard.
     * <br>
     * <b>Note: If {@link #httpVersion} is set, this setting is ignored!</b>
     *
     * @return The supported ALPN protocols.
     * @since 4.0.0
     */
    @NonNull
    String[] alpnModes() default {
        HttpVersionSelection.ALPN_HTTP_2,
        HttpVersionSelection.ALPN_HTTP_1
    };
}

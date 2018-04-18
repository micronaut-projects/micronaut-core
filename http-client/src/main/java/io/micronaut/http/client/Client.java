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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Type;
import io.micronaut.http.client.interceptor.HttpClientIntroductionAdvice;
import io.micronaut.retry.annotation.Recoverable;

import javax.inject.Scope;
import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

/**
 * A custom {@link Scope} for injecting {@link HttpClient} implementations
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Scope
@Introduction
@Type(HttpClientIntroductionAdvice.class)
@Recoverable
@Singleton
public @interface Client {

    /**
     * @return The URL or service ID of the remote service
     */
    @AliasFor(member = "id")
    String[] value() default "";

    /**
     * @return The ID of the client
     */
    @AliasFor(member = "value")
    String id() default "";

    /**
     * The base URI for the client
     *
     * @return The base URI
     */
    String path() default "";

    /**
     * @return The http client configuration bean to use
     */
    Class<? extends HttpClientConfiguration> configuration() default HttpClientConfiguration.class;
}

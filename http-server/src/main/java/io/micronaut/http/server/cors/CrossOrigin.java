/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.server.cors;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Support CORs configuration via annotation. For example, it will enable Micronaut developers only
 * to allow CORS for a few routes in their applications. Thus, having more secure
 * applications.
 * @since 3.9.0
 */
@Documented
@Inherited
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface CrossOrigin {

    /**
     *
     * @return the origins for which cross-origin requests are allowed
     */
    @AliasFor(member = "allowedOrigins")
    String[] value() default {};

    /**
     *
     * @return the origins for which cross-origin requests are allowed
     */
    @AliasFor(member = "value")
    String[] allowedOrigins() default {};

    /**
     *
     * @return regular expression to match allowed origins
     */
    String allowedOriginsRegex() default StringUtils.EMPTY_STRING;

    /**
     *
     * @return request headers permitted in requests
     */
    String[] allowedHeaders() default {};

    /**
     *
     * @return response headers that user-agent will allow client to access on actual response
     */
    String[] exposedHeaders() default {};

    /**
     *
     * @return supported HTTP request methods
     */
    HttpMethod[] allowedMethods() default {};

    /**
     *
     * @return whether the browser should send credentials
     */
    boolean allowCredentials() default true;

    /**
     *
     * @return should the browser have access to the local network
     */
    boolean allowPrivateNetwork() default true;

    /**
     *
     * @return maximum age (in seconds) of the cache duration for preflight responses
     */
    long maxAge() default 1800L;
}

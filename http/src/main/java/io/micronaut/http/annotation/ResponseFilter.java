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
package io.micronaut.http.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.EntryPoint;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.filter.FilterPatternStyle;
import org.reactivestreams.Publisher;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Method annotation for a response filter. A response filter is called after a request has been
 * sent and the response received. Possible parameter types are:
 *
 * <ul>
 *     <li>{@link HttpRequest} or {@link MutableHttpRequest}, to access the request</li>
 *     <li>{@link HttpResponse} or {@link MutableHttpResponse}, to access the response</li>
 *     <li>A {@link Throwable} type, to handle an error. If there is an error in the downstream
 *     filters, it is processed by the upstream response filters. Any response filter that does not
 *     declare a {@link Throwable} parameter of a matching type is skipped. If instead there is no
 *     downstream error, those response filters <i>with</i> a {@link Throwable} parameter are
 *     skipped, unless the parameter is {@link io.micronaut.core.annotation.Nullable}. <b>Note that
 *     for server filter execution, exceptions are transformed into non-exceptional responses with
 *     an error status code, between each filter.</b></li>
 *     <li>A {@code @}{@link Header}, {@code @}{@link QueryValue} or {@code @}{@link CookieValue}
 *     parameter</li>
 *     <li>A {@link io.micronaut.core.propagation.MutablePropagatedContext} to modify the propagated context</li>
 *     <li>A RouteMatch or RouteInfo of the route that handled this request. Note: Unless the parameter is
 *     marked as {@link io.micronaut.core.annotation.Nullable}, the filter method will <b>not</b>
 *     match for requests that do not match a route (e.g. static resources). This parameter is only
 *     supported on the server.</li>
 * </ul>
 *
 * The return value may be:
 *
 * <ul>
 *     <li>{@code void} or {@code null} to immediately continue execution, without changing the
 *     response</li>
 *     <li>An updated {@link HttpResponse}</li>
 *     <li>A {@link Publisher} (or other reactive type) that produces any of these return types, to
 *     delay further execution</li>
 * </ul>
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD})
@Inherited
@Executable
@EntryPoint
public @interface ResponseFilter {
    /**
     * Pattern used to match all requests.
     */
    String MATCH_ALL_PATTERN = Filter.MATCH_ALL_PATTERN;

    /**
     * @return The patterns this filter should match
     */
    String[] value() default {};

    /**
     * @return The style of pattern this filter uses
     */
    FilterPatternStyle patternStyle() default FilterPatternStyle.ANT;

    /**
     * Same as {@link #value()}.
     *
     * @return The patterns
     */
    @AliasFor(member = "value")
    String[] patterns() default {};

    /**
     * @return The methods to match. Defaults to all
     */
    HttpMethod[] methods() default {};
}

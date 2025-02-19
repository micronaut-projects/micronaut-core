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
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.http.filter.FilterPatternStyle;
import org.reactivestreams.Publisher;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Method annotation for a request filter. A request filter is called before the request is sent
 * out. Possible parameter types are:
 *
 * <ul>
 *     <li>{@link HttpRequest} or {@link MutableHttpRequest}, to access the request</li>
 *     <li>{@link FilterContinuation}&lt;{@link HttpResponse}&gt;,
 *     {@link FilterContinuation}&lt;{@link Publisher}&lt;{@link HttpResponse}&gt;&gt;. A call to
 *     the continuation (and, for the reactive variant, subscribing to the {@link Publisher}) will
 *     trigger execution of downstream filters, and finally perform the request. The response
 *     returned by the continuation will be the response produced by the downstream, and can be
 *     modified and returned. Note that if you call a non-reactive continuation, the call will
 *     block, which may block the netty event loop. For that reason, always mark such a filter with
 *     {@link io.micronaut.scheduling.annotation.ExecuteOn}</li>
 *     <li>A {@code @}{@link Header}, {@code @}{@link QueryValue} or {@code @}{@link CookieValue}
 *     parameter</li>
 *     <li>A {@code @}{@link Body} parameter of type {@code byte[]}, {@link String} or
 *     {@link ByteBuffer}. Only supported for some HTTP server implementations.</li>
 *     <li>A {@link io.micronaut.core.propagation.MutablePropagatedContext} to modify the propagated context</li>
 *     <li>A RouteMatch or RouteInfo of the route that will handle this request. Note: Unless the parameter is
 *     marked as {@link io.micronaut.core.annotation.Nullable}, the filter method will <b>not</b>
 *     match for requests that do not match a route (e.g. static resources). This parameter is only
 *     supported on the server.</li>
 * </ul>
 *
 * The return value may be:
 *
 * <ul>
 *     <li>{@code void} or {@code null} to immediately continue execution, without changing the
 *     request or response</li>
 *     <li>An updated {@link HttpRequest}</li>
 *     <li>A {@link HttpResponse} to skip execution of the request</li>
 *     <li>A {@link Publisher} (or other reactive type) that produces any of these return types, to
 *     delay further execution</li>
 *     <li>A {@link java.util.concurrent.CompletionStage}.
 *     <li>A {@link java.util.concurrent.CompletableFuture}. Suppose you must write a filter that proceeds with the request in some scenarios. You can use {@code CompletableFuture<@ Nullable  HttpResponse<?>>} as the return type. Then, to proceed with the request, return  {@code CompletableFuture.completedFuture(null)}.</li>
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
public @interface RequestFilter {
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

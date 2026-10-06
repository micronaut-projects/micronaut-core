/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.PathVariables;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A pending direct route, declared by a creator of the
 * {@link DirectRouteBuilder}, e.g. {@code GET(uri)}: first what
 * the server can decide from the request it received, before it creates the
 * {@link io.micronaut.http.HttpRequest}, and where the function of the route runs, see
 * {@link ExecutionSpec}: on the thread that received the request by default, or on an executor
 * with {@link #executeOn(String)}; then one terminal, which adds the route:
 * {@link #respond(HttpResponse)}, {@link #respond(Function)} or {@link #respondAsync(Function)}.
 * A second terminal, or a setting after the terminal, fails with an {@link IllegalStateException}.
 * A direct route has no filters, media types, annotations, attributes or port, and inherits
 * nothing.
 *
 * <pre>{@code
 * direct.GET("/health").order(-1).respond(HttpResponse.ok("UP"));
 * direct.GET("/ping").respondAsync(context -> pong(context));
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface DirectRouteSpec extends ExecutionSpec<DirectRouteSpec> permits DefaultDirectRouteSpec {

    /**
     * Match only the requests that meet a condition, like {@link RouteSpec#where(RouteCondition)}.
     * A direct route takes only the conditions the server decides from the request it received:
     * {@link RouteCondition#method}, {@link RouteCondition#header}, {@link RouteCondition#cookie},
     * {@link RouteCondition#query}, decoded from the URI like the parameters of a request, only
     * when a route with a query condition is tried, {@link RouteCondition#host}, which reads the
     * {@code Host} header, or the authority of an HTTP/2 request, and never a forwarded header,
     * {@link RouteCondition#peerAddress}, the time conditions {@link RouteCondition#before},
     * {@link RouteCondition#after} and {@link RouteCondition#between}, which read the
     * {@code java.time.Clock} bean if there is one, otherwise the system clock, and their
     * combinations with {@code all}, {@code any} and {@code not}. A condition of a lambda, which
     * needs the {@link io.micronaut.http.HttpRequest}, and {@link RouteCondition#remoteAddress},
     * which resolves the client address from the request and its forwarded headers, are
     * rejected with an {@link IllegalArgumentException}.
     *
     * @param condition The condition
     * @return This route
     */
    DirectRouteSpec where(RouteCondition condition);

    /**
     * Match only the requests whose path variables the constraint accepts, like
     * {@link RouteSpec#constrain(Predicate)}. The constraint runs on the thread that received
     * the request: it must be fast and must not block.
     *
     * @param accepted Whether the path variables are accepted
     * @return This route
     */
    DirectRouteSpec constrain(Predicate<? super PathVariables> accepted);

    /**
     * Constrain a path variable, like {@link RouteSpec#constrain(String, Predicate)}.
     *
     * @param variable The name of the variable
     * @param accepted Whether the value is accepted
     * @return This route
     */
    default DirectRouteSpec constrain(String variable, Predicate<? super String> accepted) {
        Objects.requireNonNull(variable, "variable");
        Objects.requireNonNull(accepted, "accepted");
        return constrain(variables -> variables.findString(variable).map(accepted::test).orElse(false));
    }

    /**
     * Constrain a path variable to a set of values, like {@link RouteSpec#constrain(String, Collection)}.
     *
     * @param variable The name of the variable
     * @param values   The accepted values
     * @return This route
     */
    default DirectRouteSpec constrain(String variable, Collection<String> values) {
        Objects.requireNonNull(values, "values");
        return constrain(variable, new ValueMatcher.OneOf(Set.copyOf(values), false));
    }

    /**
     * Constrain a path variable with a matcher of its value, like
     * {@link RouteSpec#constrain(String, ValueMatcher)}.
     *
     * @param variable The name of the variable
     * @param matcher  The matcher of the value
     * @return This route
     */
    default DirectRouteSpec constrain(String variable, ValueMatcher matcher) {
        Objects.requireNonNull(variable, "variable");
        ValueMatcher normalized = RouteConditions.normalize(Objects.requireNonNull(matcher, "matcher"));
        return constrain(variables -> normalized.matches(variables.findString(variable).orElse(null)));
    }

    /**
     * The order of the route among the direct routes that match a request equally well, like
     * {@link RouteSpec#order(int)}: the lowest order wins.
     *
     * @param order The order
     * @return This route
     */
    DirectRouteSpec order(int order);

    /**
     * End the route with a response given as a value: the route copies the status, the headers
     * and the attributes of the response for each request, and shares its body. A text body is
     * encoded once, and a {@code byte[]} body copied, now. A body the server consumes when it
     * writes it, e.g. a Netty {@code ByteBuf}, which it releases, is copied once by the server
     * runtime, and released, see
     * {@link io.micronaut.web.router.direct.DirectRouteSupport#shareableBody(Object)}.
     *
     * @param response The response
     * @throws IllegalStateException if the route was already ended
     */
    void respond(HttpResponse<?> response);

    /**
     * End the route with a function that composes the response of each request, or declines it
     * with {@code null}: the request then continues to the ordinary routes with its body
     * untouched. The function runs on the thread that received the request, e.g. an event loop,
     * unless the route runs on an executor, see {@link #executeOn(String)}.
     *
     * @param response Composes the response of a request, or returns {@code null}
     * @throws IllegalStateException if the route was already ended
     */
    void respond(Function<? super DirectContext, ? extends @Nullable HttpResponse<?>> response);

    /**
     * End the route with a function that completes the response of each request later. The stage
     * may complete on any thread; a stage completed with {@code null} declines the request, which
     * then continues to the ordinary routes with its body untouched.
     *
     * @param response Returns the stage of the response of a request
     * @throws IllegalStateException if the route was already ended
     */
    void respondAsync(Function<? super DirectContext, ? extends CompletionStage<? extends @Nullable HttpResponse<?>>> response);
}

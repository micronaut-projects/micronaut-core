/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.uri.UriMatchInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Accessors for basic attributes outside micronaut-http-router.
 *
 * @author Jonas Konrad
 * @since 4.8.0
 */
@SuppressWarnings("removal")
public final class BasicHttpAttributes {
    private static final String ROUTE_WAITS_FOR = BasicHttpAttributes.class.getName() + ".ROUTE_WAITS_FOR";
    private static final Logger LOG = LoggerFactory.getLogger(BasicHttpAttributes.class);
    private static final String UNCONSUMED_BODY_DISCARDS = BasicHttpAttributes.class.getName() + ".UNCONSUMED_BODY_DISCARDS";

    private BasicHttpAttributes() {
    }

    /**
     * Get the route match as a {@link UriMatchInfo}.
     *
     * @param request The request
     * @return The route match, if present
     */
    public static Optional<UriMatchInfo> getRouteMatchInfo(HttpRequest<?> request) {
        return request.getAttribute(HttpAttributes.ROUTE_MATCH, UriMatchInfo.class);
    }

    /**
     * Get the URI template as a String, for tracing.
     *
     * @param request The request
     * @return The template, if present
     */
    public static Optional<String> getUriTemplate(HttpRequest<?> request) {
        return request.getAttribute(HttpAttributes.URI_TEMPLATE, String.class);
    }

    /**
     * Set the URI template as a String, for tracing.
     *
     * @param request     The request
     * @param uriTemplate The template, if present
     */
    public static void setUriTemplate(HttpRequest<?> request, String uriTemplate) {
        request.setAttribute(HttpAttributes.URI_TEMPLATE, uriTemplate);
    }

    /**
     * Get the client service ID.
     *
     * @param request The request
     * @return The client service ID
     */
    public static Optional<String> getServiceId(HttpRequest<?> request) {
        return request.getAttribute(HttpAttributes.SERVICE_ID, String.class);
    }

    /**
     * A condition that must be awaited before executing controllers for the given request. This is
     * used to delay execution for argument binding.
     *
     * @param request The request
     * @return The condition to wait for
     */
    @Experimental
    public static ExecutionFlow<?> getRouteWaitsFor(HttpRequest<?> request) {
        @SuppressWarnings("rawtypes")
        Optional<ExecutionFlow> attr = request.getAttribute(ROUTE_WAITS_FOR, ExecutionFlow.class);
        return attr.orElseGet(ExecutionFlow::empty);
    }

    /**
     * Remove the conditions that must be awaited before executing controllers for the given
     * request and return them.
     *
     * @param request The request
     * @return The condition to wait for
     * @since 5.2.18
     */
    @Internal
    public static ExecutionFlow<?> takeRouteWaitsFor(HttpRequest<?> request) {
        @SuppressWarnings("rawtypes")
        Optional<ExecutionFlow> attr = request.removeAttribute(ROUTE_WAITS_FOR, ExecutionFlow.class);
        return attr.orElseGet(ExecutionFlow::empty);
    }

    /**
     * Add a condition that must be awaited before executing controllers for the given request.
     * This is used to delay execution for argument binding.
     *
     * @param request The request
     * @param flowToAdd The condition to wait for
     */
    @Experimental
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addRouteWaitsFor(HttpRequest<?> request, ExecutionFlow<?> flowToAdd) {
        Optional<ExecutionFlow> attr = request.getAttribute(ROUTE_WAITS_FOR, ExecutionFlow.class);
        if (attr.isPresent()) {
            request.setAttribute(ROUTE_WAITS_FOR, attr.get().then(() -> flowToAdd));
        } else {
            request.setAttribute(ROUTE_WAITS_FOR, flowToAdd);
        }
    }

    /**
     * Register an action that discards a streaming body argument the route does not consume.
     * Argument binders that claim the request body before the route executes (e.g. for a
     * {@code Publisher} or {@code InputStream} argument) use this so that the body is not left
     * buffered, and the connection stalled, when the request is rejected, e.g. because another
     * argument fails to bind or a {@code @Valid} constraint is violated, or when the route
     * returns without ever subscribing to the argument. The action must leave a body alone that
     * the route has started to consume.
     *
     * @param request The request
     * @param discard The action that discards the body
     */
    @Internal
    public static void addUnconsumedBodyDiscard(HttpRequest<?> request, Runnable discard) {
        addUnconsumedBodyDiscard(request, discard, discard);
    }

    /**
     * Register the actions that discard a streaming body argument the route does not consume.
     *
     * @param request         The request
     * @param onRouteFailure  The action that discards the body if the route fails before it returns
     * @param afterResponse   The action that discards the body once the response has been written,
     *                        if the route never started to consume it
     */
    @Internal
    public static void addUnconsumedBodyDiscard(HttpRequest<?> request, Runnable onRouteFailure, Runnable afterResponse) {
        UnconsumedBodyDiscards discards = request.getAttribute(UNCONSUMED_BODY_DISCARDS, UnconsumedBodyDiscards.class).orElse(null);
        if (discards == null) {
            discards = new UnconsumedBodyDiscards();
            request.setAttribute(UNCONSUMED_BODY_DISCARDS, discards);
        }
        discards.add(onRouteFailure, afterResponse);
    }

    /**
     * Run the actions registered with {@link #addUnconsumedBodyDiscard}. This is called when the
     * route is not invoked, or its invocation throws, for a request whose arguments were
     * (partially) bound. Each registration runs at most once.
     *
     * @param request The request
     */
    @Internal
    public static void discardUnconsumedBodies(HttpRequest<?> request) {
        Optional<UnconsumedBodyDiscards> discards = request.getAttribute(UNCONSUMED_BODY_DISCARDS, UnconsumedBodyDiscards.class);
        discards.ifPresent(d -> d.run(true));
    }

    /**
     * Discard the streaming body arguments the route never started to consume. This is called
     * once the response has been written. Each registration runs at most once.
     *
     * @param request The request
     */
    @Internal
    public static void discardUnsubscribedBodies(HttpRequest<?> request) {
        Optional<UnconsumedBodyDiscards> discards = request.getAttribute(UNCONSUMED_BODY_DISCARDS, UnconsumedBodyDiscards.class);
        discards.ifPresent(d -> d.run(false));
    }

    private static final class UnconsumedBodyDiscards {
        private List<Discard> actions = new ArrayList<>(1);

        synchronized void add(Runnable onRouteFailure, Runnable afterResponse) {
            actions.add(new Discard(onRouteFailure, afterResponse));
        }

        void run(boolean routeFailed) {
            List<Discard> actions;
            synchronized (this) {
                actions = this.actions;
                if (actions.isEmpty()) {
                    return;
                }
                this.actions = new ArrayList<>(1);
            }
            for (Discard action : actions) {
                try {
                    (routeFailed ? action.onRouteFailure : action.afterResponse).run();
                } catch (Exception e) {
                    LOG.debug("Failed to discard unconsumed request body", e);
                }
            }
        }
    }

    private record Discard(Runnable onRouteFailure, Runnable afterResponse) {
    }
}

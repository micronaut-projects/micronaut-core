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
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.body.ReleasableRequestBody;
import io.micronaut.http.uri.UriMatchInfo;
import org.jspecify.annotations.Nullable;

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
    private static final String ROUTE_BODIES = BasicHttpAttributes.class.getName() + ".ROUTE_BODIES";

    private BasicHttpAttributes() {
    }

    /**
     * Get the route match as a {@link UriMatchInfo}.
     *
     * @param request The request
     * @return The route match, if present
     */
    public static Optional<UriMatchInfo> getRouteMatchInfo(HttpRequest<?> request) {
        if (request instanceof RouteMetadataHolder holder) {
            return holder.getRouteMatchMetadata() instanceof UriMatchInfo info ? Optional.of(info) : Optional.empty();
        }
        return request.getAttribute(HttpAttributes.ROUTE_MATCH, UriMatchInfo.class);
    }

    /**
     * Get the URI template as a String, for tracing.
     *
     * @param request The request
     * @return The template, if present
     */
    public static Optional<String> getUriTemplate(HttpRequest<?> request) {
        if (request instanceof RouteMetadataHolder holder) {
            return Optional.ofNullable(holder.getUriTemplateMetadata());
        }
        return request.getAttribute(HttpAttributes.URI_TEMPLATE, String.class);
    }

    /**
     * Set the URI template as a String, for tracing.
     *
     * @param request     The request
     * @param uriTemplate The template, if present
     */
    public static void setUriTemplate(HttpRequest<?> request, String uriTemplate) {
        if (request instanceof RouteMetadataHolder holder) {
            holder.setUriTemplateMetadata(uriTemplate);
        } else {
            request.setAttribute(HttpAttributes.URI_TEMPLATE, uriTemplate);
        }
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
        // getAttribute(name) without a type: the typed lookup allocates a conversion context even
        // when the attribute is absent, which it is for nearly every request
        if (request.getAttribute(ROUTE_WAITS_FOR).orElse(null) instanceof ExecutionFlow<?> flow) {
            return flow;
        }
        return ExecutionFlow.empty();
    }

    /**
     * Add a condition that must be awaited before executing controllers for the given request.
     * This is used to delay execution for argument binding.
     *
     * <p>A request that is a {@link DetachedBinding} keeps the condition itself: it is bound
     * outside the argument binding of the route, which does not wait for it.</p>
     *
     * @param request The request
     * @param flowToAdd The condition to wait for
     */
    @Experimental
    public static void addRouteWaitsFor(HttpRequest<?> request, ExecutionFlow<?> flowToAdd) {
        if (request instanceof DetachedBinding detached) {
            detached.addWaitsFor(flowToAdd);
            return;
        }
        if (request.getAttribute(ROUTE_WAITS_FOR).orElse(null) instanceof ExecutionFlow<?> existing) {
            request.setAttribute(ROUTE_WAITS_FOR, existing.then(() -> flowToAdd));
        } else {
            request.setAttribute(ROUTE_WAITS_FOR, flowToAdd);
        }
    }

    /**
     * Add a body the route of the request is invoked with, which is released when the route
     * completed, before its response is written, or, for a streamed response, when its stream
     * ended, see {@link #takeRouteBodies}.
     *
     * @param request The request
     * @param body    The body, bound for the route
     * @since 5.3.0
     */
    @Internal
    public static void addRouteBody(HttpRequest<?> request, ReleasableRequestBody body) {
        if (request.getAttribute(ROUTE_BODIES).orElse(null) instanceof ReleasableRequestBody existing) {
            request.setAttribute(ROUTE_BODIES, ReleasableRequestBody.both(existing, body));
        } else {
            request.setAttribute(ROUTE_BODIES, body);
        }
    }

    /**
     * Whether the route of the request was invoked with a body to release, see
     * {@link #addRouteBody}.
     *
     * @param request The request
     * @return {@code true} if the request has a body its route releases
     * @since 5.3.0
     */
    @Internal
    public static boolean hasRouteBodies(HttpRequest<?> request) {
        // getAttribute(name) without a type, see getRouteWaitsFor
        return request.getAttribute(ROUTE_BODIES).orElse(null) instanceof ReleasableRequestBody;
    }

    /**
     * Take the bodies the route of the request was invoked with, see {@link #addRouteBody}: the
     * caller releases them once the route completed, or, for a streamed response, when its
     * stream ended.
     *
     * @param request The request
     * @return The bodies, or {@code null} if the route was not invoked with one
     * @since 5.3.0
     */
    @Internal
    public static @Nullable ReleasableRequestBody takeRouteBodies(HttpRequest<?> request) {
        // getAttribute(name) without a type, see getRouteWaitsFor
        if (request.getAttribute(ROUTE_BODIES).orElse(null) instanceof ReleasableRequestBody bodies) {
            request.getAttributes().remove(ROUTE_BODIES);
            return bodies;
        }
        return null;
    }

    /**
     * Run a binding outside the argument binding of a route: the conditions the binding adds
     * with {@link #addRouteWaitsFor} are returned to the caller instead of delaying the route,
     * and the conditions of the route are left as they were. Used to bind a value while the
     * route runs, e.g. the body an asynchronous handler reads, or for another method, e.g. a
     * filter method. The bodies the binding adds with {@link #addRouteBody} are not the route's
     * either: the caller releases what it bound, e.g. a filter method releases its body when it
     * completed.
     *
     * <p>Not thread-safe: the conditions and the bodies of the route are removed from the
     * attributes of the request while the binding runs, and restored afterwards. It is called
     * while the value is bound, on one thread, and never while another binding of the request
     * runs, e.g. the argument binding of the route or another detached binding. A binding that
     * can run while another one does binds from a request of its own instead, which keeps the
     * conditions, see {@link DetachedBinding}.</p>
     *
     * @param request The request
     * @param binding The binding
     * @return What the binding waits for
     * @since 5.3.0
     */
    @Internal
    public static ExecutionFlow<?> detachRouteWaitsFor(HttpRequest<?> request, Runnable binding) {
        DetachedRouteState detached = detachRouteState(request);
        try {
            binding.run();
            return getRouteWaitsFor(request);
        } finally {
            restoreRouteState(request, detached);
        }
    }

    /**
     * Detach the conditions and the bodies of the route from the request before a binding that is
     * not the route's, like {@link #detachRouteWaitsFor}, without allocating anything when the
     * request has none, which is the case for nearly every request: the caller binds, reads
     * {@link #getRouteWaitsFor} if it needs what the binding waits for, and calls
     * {@link #restoreRouteState} in a {@code finally} block.
     *
     * @param request The request
     * @return The detached state, or {@code null} if the request had none
     * @since 5.3.0
     */
    @Internal
    public static @Nullable DetachedRouteState detachRouteState(HttpRequest<?> request) {
        // getAttribute(name) without a type, see getRouteWaitsFor: no attribute map is created
        Object waitsFor = request.getAttribute(ROUTE_WAITS_FOR).orElse(null);
        Object bodies = request.getAttribute(ROUTE_BODIES).orElse(null);
        if (waitsFor == null && bodies == null) {
            return null;
        }
        MutableConvertibleValues<Object> attributes = request.getAttributes();
        attributes.remove(ROUTE_WAITS_FOR);
        attributes.remove(ROUTE_BODIES);
        return new DetachedRouteState(waitsFor, bodies);
    }

    /**
     * Restore the conditions and the bodies of the route that {@link #detachRouteState} detached,
     * dropping those the binding added in between.
     *
     * @param request  The request
     * @param detached The detached state, or {@code null} if the request had none
     * @since 5.3.0
     */
    @Internal
    public static void restoreRouteState(HttpRequest<?> request, @Nullable DetachedRouteState detached) {
        restore(request, ROUTE_WAITS_FOR, detached == null ? null : detached.waitsFor());
        restore(request, ROUTE_BODIES, detached == null ? null : detached.bodies());
    }

    private static void restore(HttpRequest<?> request, String name, @Nullable Object previous) {
        if (previous != null) {
            request.getAttributes().put(name, previous);
        } else if (request.getAttribute(name).isPresent()) {
            request.getAttributes().remove(name);
        }
    }

    /**
     * The conditions and the bodies of a route, detached from its request while a binding that
     * is not the route's runs, see {@link #detachRouteState}.
     *
     * @param waitsFor The conditions, or {@code null}
     * @param bodies   The bodies, or {@code null}
     * @since 5.3.0
     */
    @Internal
    public record DetachedRouteState(@Nullable Object waitsFor, @Nullable Object bodies) {
    }

    /**
     * A request a value is bound from outside the argument binding of a route, e.g. a wrapper of
     * the request of the route made for one binding: it keeps the conditions the binding adds
     * with {@link #addRouteWaitsFor} itself, for the caller of the binding to wait for. No
     * attribute of the request holds them, so bindings of one request that run at the same time
     * do not see or replace the conditions of one another, unlike with
     * {@link #detachRouteWaitsFor}.
     *
     * @since 5.3.0
     */
    @Internal
    public interface DetachedBinding {

        /**
         * Add a condition the binding waits for.
         *
         * @param flow The condition
         */
        void addWaitsFor(ExecutionFlow<?> flow);
    }
}

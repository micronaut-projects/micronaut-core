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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.LifecycleHttpRequest;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Releases bound argument values that are owned by the route (for example a refcounted body)
 * when the request ends before the route was invoked. Each value belongs to the route whose
 * binding produced it, so invoking another route for the same request (an error or status
 * route) does not take over the values of a route that never ran.
 *
 * @since 5.2.21
 */
@Internal
public final class UnconsumedArgumentRelease {
    private static final String ATTRIBUTE = UnconsumedArgumentRelease.class.getName();

    private final List<Pending> releases = new ArrayList<>(1);
    private final List<RouteInfo<?>> invoked = new ArrayList<>(1);
    private boolean released;

    private UnconsumedArgumentRelease() {
    }

    /**
     * Release a bound argument value with the request, unless the route that is currently
     * matched for the request is invoked first.
     *
     * @param request The request
     * @param release Releases the value
     */
    public static void add(LifecycleHttpRequest<?> request, Runnable release) {
        UnconsumedArgumentRelease pending = request.getAttribute(ATTRIBUTE, UnconsumedArgumentRelease.class).orElse(null);
        if (pending == null) {
            pending = new UnconsumedArgumentRelease();
            request.setAttribute(ATTRIBUTE, pending);
            request.addDisposalResource(pending::release);
        }
        pending.addRelease(RouteAttributes.getRouteInfo(request).orElse(null), release);
    }

    /**
     * Called when a route is invoked: the route now owns its argument values.
     *
     * @param request The request
     * @param routeInfo The invoked route
     */
    public static void routeInvoked(HttpRequest<?> request, RouteInfo<?> routeInfo) {
        request.getAttribute(ATTRIBUTE, UnconsumedArgumentRelease.class).ifPresent(p -> p.transfer(routeInfo));
    }

    private void addRelease(@Nullable RouteInfo<?> route, Runnable release) {
        synchronized (this) {
            if (!released) {
                if (!isInvoked(route)) {
                    releases.add(new Pending(route, release));
                }
                return;
            }
        }
        // the request is already released, nobody would run this later
        release.run();
    }

    private boolean isInvoked(@Nullable RouteInfo<?> route) {
        for (RouteInfo<?> r : invoked) {
            if (r == route) {
                return true;
            }
        }
        return false;
    }

    private synchronized void transfer(RouteInfo<?> route) {
        invoked.add(route);
        Iterator<Pending> it = releases.iterator();
        while (it.hasNext()) {
            if (it.next().route == route) {
                it.remove();
            }
        }
    }

    private void release() {
        List<Pending> toRun;
        synchronized (this) {
            released = true;
            toRun = new ArrayList<>(releases);
            releases.clear();
        }
        for (Pending p : toRun) {
            p.release.run();
        }
    }

    private record Pending(@Nullable RouteInfo<?> route, Runnable release) {
    }
}

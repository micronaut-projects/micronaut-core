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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.LifecycleHttpRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Releases bound argument values that are owned by the route (for example a refcounted body)
 * when the request ends before the route was invoked.
 *
 * @since 5.2.18
 */
@Internal
public final class UnconsumedArgumentRelease {
    private static final String ATTRIBUTE = UnconsumedArgumentRelease.class.getName();

    private final List<Runnable> releases = new ArrayList<>(1);
    private boolean invoked;
    private boolean released;

    private UnconsumedArgumentRelease() {
    }

    /**
     * Release a bound argument value with the request, unless the route is invoked first.
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
        pending.addRelease(release);
    }

    /**
     * Called when the route is invoked: the route now owns its argument values.
     *
     * @param request The request
     */
    public static void routeInvoked(HttpRequest<?> request) {
        request.getAttribute(ATTRIBUTE, UnconsumedArgumentRelease.class).ifPresent(UnconsumedArgumentRelease::transfer);
    }

    private synchronized void addRelease(Runnable release) {
        if (released) {
            // the request is already released, nobody would run this later
            release.run();
        } else if (!invoked) {
            releases.add(release);
        }
    }

    private synchronized void transfer() {
        invoked = true;
        releases.clear();
    }

    private void release() {
        List<Runnable> toRun;
        synchronized (this) {
            released = true;
            if (invoked) {
                return;
            }
            toRun = new ArrayList<>(releases);
            releases.clear();
        }
        for (Runnable r : toRun) {
            r.run();
        }
    }
}

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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.RouteArguments;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The media types and the executor of a {@link HttpRouteGroup}, which the routes of the group
 * and of its nested groups inherit unless they set their own. A nested group's setting overrides
 * the setting of the groups around it.
 *
 * <p>The settings are given to the routes when the outermost group is closed, once every route
 * and every setting of the lambdas was declared: a setting applies to every route declared in the
 * lambda of its group, wherever it is declared in the lambda, like the filters of the group. A
 * route that sets its own value, when it is declared or later, keeps it.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class RouteGroupDefaults {

    /**
     * The media types the routes consume.
     */
    static final int CONSUMES = 1;
    /**
     * The media types the routes produce.
     */
    static final int PRODUCES = 1 << 1;
    /**
     * The executor the routes run on, or the event loop.
     */
    static final int EXECUTOR = 1 << 2;

    private final @Nullable RouteGroupDefaults enclosing;
    /**
     * The routes of the outermost group and of the groups nested in it, shared by them: they
     * inherit their settings when the outermost group is closed.
     */
    private final List<Inheriting> routes;
    private @Nullable Consumer<RouteSettings> consumes;
    private @Nullable Consumer<RouteSettings> produces;
    private @Nullable Consumer<RouteSettings> executor;
    private boolean closed;

    /**
     * @param enclosing The settings of the enclosing group, or {@code null} for an outermost group
     */
    RouteGroupDefaults(@Nullable RouteGroupDefaults enclosing) {
        this.enclosing = enclosing;
        this.routes = enclosing == null ? new ArrayList<>() : enclosing.routes;
    }

    /**
     * @param mediaTypes The media types the routes of the group consume
     * @see HttpRouteGroup#consumes(MediaType...)
     */
    void consumes(MediaType[] mediaTypes) {
        MediaType[] checked = AbstractHttpRouteBuilder.mediaTypes(mediaTypes);
        checkOpen();
        consumes = route -> route.consumes(checked);
    }

    /**
     * @see HttpRouteGroup#consumesAll()
     */
    void consumesAll() {
        checkOpen();
        consumes = RouteSettings::consumesAll;
    }

    /**
     * @param mediaTypes The media types the routes of the group produce
     * @see HttpRouteGroup#produces(MediaType...)
     */
    void produces(MediaType[] mediaTypes) {
        MediaType[] checked = AbstractHttpRouteBuilder.mediaTypes(mediaTypes);
        checkOpen();
        produces = route -> route.produces(checked);
    }

    /**
     * @param executorName The executor the routes of the group run on
     * @see HttpRouteGroup#executeOn(String)
     */
    void executeOn(String executorName) {
        String name = RouteArguments.executorName(executorName);
        checkOpen();
        executor = route -> route.executeOn(name);
    }

    /**
     * @see HttpRouteGroup#nonBlocking()
     */
    void nonBlocking() {
        checkOpen();
        executor = RouteSettings::nonBlocking;
    }

    /**
     * Declare routes of a handler in the group.
     *
     * @param handlerRoutes The routes of the handler
     * @param own           The settings the routes have of their own, e.g. {@link #CONSUMES} for a
     *                      form handler, which consumes forms: they do not inherit them
     * @return The routes, whose settings the spec of the routes marks as their own
     */
    Inheriting add(List<RouteSettings> handlerRoutes, int own) {
        Inheriting inheriting = new Inheriting(this, handlerRoutes, own);
        routes.add(inheriting);
        return inheriting;
    }

    /**
     * Close the group: its lambda returned. When it is the outermost group, its routes and the
     * routes of its nested groups inherit their settings.
     */
    void close() {
        closed = true;
        if (enclosing == null) {
            for (Inheriting inheriting : routes) {
                inheriting.inherit();
            }
            routes.clear();
        }
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("The route group is closed: declare the settings of a group in its lambda");
        }
    }

    /**
     * @param setting A setting, e.g. {@link #CONSUMES}
     * @return The setting of this group, or of the closest enclosing group that has it, or {@code null}
     */
    private @Nullable Consumer<RouteSettings> resolve(int setting) {
        RouteGroupDefaults group = this;
        while (group != null) {
            Consumer<RouteSettings> value = switch (setting) {
                case CONSUMES -> group.consumes;
                case PRODUCES -> group.produces;
                default -> group.executor;
            };
            if (value != null) {
                return value;
            }
            group = group.enclosing;
        }
        return null;
    }

    /**
     * The routes of a handler declared in a group, and the settings they have of their own.
     */
    static final class Inheriting {
        private final RouteGroupDefaults group;
        private final List<RouteSettings> routes;
        private int own;

        private Inheriting(RouteGroupDefaults group, List<RouteSettings> routes, int own) {
            this.group = group;
            this.routes = routes;
            this.own = own;
        }

        /**
         * The routes set a setting of their own: they do not inherit it.
         *
         * @param setting The setting, e.g. {@link #CONSUMES}
         */
        void own(int setting) {
            own |= setting;
        }

        private void inherit() {
            inherit(CONSUMES);
            inherit(PRODUCES);
            inherit(EXECUTOR);
        }

        private void inherit(int setting) {
            if ((own & setting) != 0) {
                return;
            }
            Consumer<RouteSettings> value = group.resolve(setting);
            if (value != null) {
                for (RouteSettings route : routes) {
                    value.accept(route);
                }
            }
        }
    }
}

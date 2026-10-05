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

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.PathVariables;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * The routes of a handler, one, or one per HTTP method, which the settings of their
 * {@link HttpRouteSpec}, recorded before its terminal, are given to once the terminal added them.
 * The arguments were checked when the settings were recorded.
 *
 * @param routes    The routes of the handler
 * @param inherited The media types and the executor the routes inherit from their groups, or {@code null}
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record HandlerRoutes(List<RouteSettings> routes, RouteGroupDefaults.@Nullable Inheriting inherited) {

    void consumes(MediaType[] mediaTypes) {
        own(RouteGroupDefaults.CONSUMES_SETTING);
        for (RouteSettings route : routes) {
            route.consumes(mediaTypes);
        }
    }

    void consumesAll() {
        own(RouteGroupDefaults.CONSUMES_SETTING);
        for (RouteSettings route : routes) {
            route.consumesAll();
        }
    }

    void produces(MediaType[] mediaTypes) {
        own(RouteGroupDefaults.PRODUCES_SETTING);
        for (RouteSettings route : routes) {
            route.produces(mediaTypes);
        }
    }

    void annotationMetadata(AnnotationMetadataProvider annotationMetadata) {
        for (RouteSettings route : routes) {
            route.annotationMetadata(annotationMetadata);
        }
    }

    void annotate(AnnotationValue<?> annotationValue) {
        for (RouteSettings route : routes) {
            route.annotate(annotationValue);
        }
    }

    void responseType(Argument<?> responseType) {
        for (RouteSettings route : routes) {
            route.responseType(responseType);
        }
    }

    void executeOn(String executorName) {
        own(RouteGroupDefaults.EXECUTOR_SETTING);
        for (RouteSettings route : routes) {
            route.executeOn(executorName);
        }
    }

    void nonBlocking() {
        own(RouteGroupDefaults.EXECUTOR_SETTING);
        for (RouteSettings route : routes) {
            route.nonBlocking();
        }
    }

    void port(int port) {
        for (RouteSettings route : routes) {
            route.port(port);
        }
    }

    void attribute(String name, Object value) {
        for (RouteSettings route : routes) {
            route.attribute(name, value);
        }
    }

    void order(int order) {
        for (RouteSettings route : routes) {
            route.order(order);
        }
    }

    void where(RouteCondition condition) {
        for (RouteSettings route : routes) {
            route.where(condition);
        }
    }

    void constrain(Predicate<? super PathVariables> accepted) {
        for (RouteSettings route : routes) {
            route.constrain(accepted);
        }
    }

    void filter(FilterRegistration filter) {
        for (RouteSettings route : routes) {
            route.filter(filter);
        }
    }

    /**
     * The routes set a setting of their own: they no longer inherit the setting of their group.
     *
     * @param setting The setting
     */
    private void own(int setting) {
        RouteGroupDefaults.Inheriting grouped = inherited;
        if (grouped != null) {
            grouped.own(setting);
        }
    }
}

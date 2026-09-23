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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The {@link HttpRouteSpec}: the routes of a handler, one, or one per HTTP method, configured
 * together. The spec holds no state of its own, the routes do: it compares by its routes.
 *
 * @param routes The routes of the handler
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record DefaultHttpRouteSpec(List<HandlerUriRoute> routes) implements HttpRouteSpec, ContextFilterSpec<HttpRouteSpec> {

    /**
     * @param route The route of the handler
     * @return The spec of the route
     */
    static DefaultHttpRouteSpec of(HandlerUriRoute route) {
        return new DefaultHttpRouteSpec(List.of(route));
    }

    @Override
    public HttpRouteSpec consumes(MediaType... mediaTypes) {
        MediaType[] checked = AbstractHttpRouteBuilder.mediaTypes(mediaTypes);
        for (HandlerUriRoute route : routes) {
            route.consumes(checked);
        }
        return this;
    }

    @Override
    public HttpRouteSpec consumesAll() {
        for (HandlerUriRoute route : routes) {
            route.consumesAll();
        }
        return this;
    }

    @Override
    public HttpRouteSpec produces(MediaType... mediaTypes) {
        MediaType[] checked = AbstractHttpRouteBuilder.mediaTypes(mediaTypes);
        for (HandlerUriRoute route : routes) {
            route.produces(checked);
        }
        return this;
    }

    @Override
    public HttpRouteSpec annotationMetadata(AnnotationMetadataProvider annotationMetadata) {
        for (HandlerUriRoute route : routes) {
            route.annotationMetadata(annotationMetadata);
        }
        return this;
    }

    @Override
    public HttpRouteSpec responseType(Argument<?> responseType) {
        Objects.requireNonNull(responseType, "responseType");
        for (HandlerUriRoute route : routes) {
            route.responseType(responseType);
        }
        return this;
    }

    @Override
    public HttpRouteSpec executeOn(String executorName) {
        for (HandlerUriRoute route : routes) {
            route.executeOn(executorName);
        }
        return this;
    }

    @Override
    public HttpRouteSpec nonBlocking() {
        for (HandlerUriRoute route : routes) {
            route.nonBlocking();
        }
        return this;
    }

    @Override
    public HttpRouteSpec port(int port) {
        for (HandlerUriRoute route : routes) {
            route.port(port);
        }
        return this;
    }

    @Override
    public HttpRouteSpec attribute(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        for (HandlerUriRoute route : routes) {
            route.attribute(name, value);
        }
        return this;
    }

    @Override
    public HttpRouteSpec order(int order) {
        for (HandlerUriRoute route : routes) {
            route.order(order);
        }
        return this;
    }

    @Override
    public HttpRouteSpec where(Predicate<HttpRequest<?>> condition) {
        Objects.requireNonNull(condition, "condition");
        for (HandlerUriRoute route : routes) {
            route.where(condition);
        }
        return this;
    }

    @Override
    public HttpRouteSpec before(ContextRouteRequestFilter filter) {
        for (HandlerUriRoute route : routes) {
            route.before(filter);
        }
        return this;
    }

    @Override
    public HttpRouteSpec before(String executorName, ContextRouteRequestFilter filter) {
        for (HandlerUriRoute route : routes) {
            route.before(executorName, filter);
        }
        return this;
    }

    @Override
    public HttpRouteSpec beforeAsync(AsyncContextRouteRequestFilter filter) {
        for (HandlerUriRoute route : routes) {
            route.beforeAsync(filter);
        }
        return this;
    }

    @Override
    public HttpRouteSpec afterReplacing(ContextReplacingRouteResponseFilter filter) {
        for (HandlerUriRoute route : routes) {
            route.after(filter);
        }
        return this;
    }

    @Override
    public HttpRouteSpec afterReplacing(String executorName, ContextReplacingRouteResponseFilter filter) {
        for (HandlerUriRoute route : routes) {
            route.after(executorName, filter);
        }
        return this;
    }

    @Override
    public HttpRouteSpec afterReplacingAsync(AsyncContextReplacingRouteResponseFilter filter) {
        for (HandlerUriRoute route : routes) {
            route.afterAsync(filter);
        }
        return this;
    }
}

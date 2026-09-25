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
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.RouteAssembly;

/**
 * The {@link ErrorRouteSpec}: configures the error route and its handler, which hold the
 * configuration.
 *
 * @param route   The error route
 * @param handler The handler of the route
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record DefaultErrorRouteSpec(RouteAssembly.DefaultErrorRoute route, HandlerMethod<?> handler) implements ErrorRouteSpec {

    @Override
    public ErrorRouteSpec produces(MediaType... mediaTypes) {
        route.produces(AbstractHttpRouteBuilder.mediaTypes(mediaTypes));
        return this;
    }

    @Override
    public ErrorRouteSpec responseType(Argument<?> responseType) {
        handler.responseType(responseType);
        return this;
    }
}

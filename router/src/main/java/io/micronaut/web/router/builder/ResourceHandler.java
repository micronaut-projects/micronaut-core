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

/**
 * A {@link RequestHandler} that serves the resources under a URI prefix: it reads the path of the
 * resource, relative to the prefix, from the path variable {@link #pathVariable()}.
 * {@link HttpRouteBuilder#resources(String, ResourceHandler)} routes the prefix, and every path
 * under it with that variable, to the handler. The static resources of the HTTP server,
 * {@code io.micronaut.http.server.routes.StaticResources}, are one.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 * @see HttpRouteBuilder#resources(String, ResourceHandler)
 */
@Experimental
public interface ResourceHandler extends RequestHandler {

    /**
     * The name of the path variable the handler reads the path of the resource from. The value is
     * relative to the URI prefix, without a leading slash; the route of the prefix itself has no
     * such variable.
     *
     * @return The name of the path variable
     */
    String pathVariable();

}

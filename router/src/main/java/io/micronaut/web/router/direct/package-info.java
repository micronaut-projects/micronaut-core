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
/**
 * The SPI between the router and the server runtimes for direct routes: routes the server
 * answers as soon as it has received a request, before it creates the
 * {@link io.micronaut.http.HttpRequest}, runs the filters and looks up the ordinary routes. The
 * application declares them with {@link io.micronaut.web.router.builder.HttpDirectRoutes} beans.
 *
 * <p>A server runtime that answers direct routes, e.g. the Netty server or a servlet container,
 * declares a {@link io.micronaut.web.router.direct.DirectRouteSupport} bean, and looks up the
 * response of a request with {@link io.micronaut.web.router.direct.DirectRouteLookup} before it
 * creates the {@link io.micronaut.http.HttpRequest}, from the method, the path, the headers and
 * the peer address of the request it received, a
 * {@link io.micronaut.web.router.direct.DirectRequest}. An asynchronous route answers with a
 * {@link io.micronaut.web.router.direct.PendingResponse}.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@NullMarked
package io.micronaut.web.router.direct;

import org.jspecify.annotations.NullMarked;

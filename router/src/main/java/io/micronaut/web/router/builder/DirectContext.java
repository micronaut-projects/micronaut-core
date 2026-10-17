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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.direct.DirectRequest;

import java.util.Map;

/**
 * What the function of a direct route receives for a request, see
 * {@link DirectRouteSpec#respond(java.util.function.Function)}: the request as the server
 * received it, and the path variables of the matched route. There is no
 * {@link io.micronaut.http.HttpRequest}: a direct route is answered before the server creates it.
 *
 * <pre>{@code
 * routes.GET("/greetings/{name}").respond(direct -> HttpResponse.ok("Hello " + direct.pathVariables().getString("name"))
 *     .contentType(MediaType.TEXT_PLAIN_TYPE));
 * routes.GET("/version").respond(direct -> VERSION.equals(direct.request().header(HttpHeaders.IF_NONE_MATCH))
 *     ? HttpResponse.notModified()
 *     : HttpResponse.ok(BODY).header(HttpHeaders.ETAG, VERSION));
 * }</pre>
 *
 * <p>The function creates its response with the static methods of
 * {@link io.micronaut.http.HttpResponse}, which create the responses of the server runtime: the
 * server writes them as they are, without converting them. A response of another type, e.g. one
 * a library returns, is converted.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface DirectContext permits DefaultDirectContext, DirectRouteTable.FunctionMatch {

    /**
     * The request as the server received it: its method, path, headers, query parameters and
     * peer address, each read when it is asked for. A route of several methods, or a
     * {@code GET} route that also answers {@code HEAD} requests, reads the method of the request
     * here.
     *
     * @return The request
     */
    DirectRequest request();

    /**
     * @return The path variables of the matched route, converted like the arguments of a handler
     */
    PathVariables pathVariables();

    /**
     * A context of a request, e.g. to test the function of a direct route without a server.
     *
     * @param request       The request
     * @param pathVariables The values of the path variables, by their names, copied, converted
     *                      with the shared conversion service
     * @return The context
     */
    static DirectContext of(DirectRequest request, Map<String, Object> pathVariables) {
        return new DefaultDirectContext(request, new DefaultPathVariables(Map.copyOf(pathVariables), ConversionService.SHARED));
    }
}

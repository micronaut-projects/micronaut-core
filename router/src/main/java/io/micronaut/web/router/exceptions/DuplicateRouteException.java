/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.web.router.exceptions;

import io.micronaut.web.router.UriRouteMatch;

import java.util.List;
import java.util.stream.Collectors;

/**
 * An exception thrown when multiple routes match a given URI.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class DuplicateRouteException extends RoutingException {

    private final String uri;
    private final List<UriRouteMatch<Object, Object>> uriRoutes;

    /**
     * @param uri The URI
     * @param uriRoutes The routes
     */
    public DuplicateRouteException(String uri, List<UriRouteMatch<Object, Object>> uriRoutes) {
        super(buildMessage(uri, uriRoutes));
        this.uri = uri;
        this.uriRoutes = uriRoutes;
    }

    /**
     * @return The uri
     */
    public String getUri() {
        return uri;
    }

    /**
     *
     * @return The routes which caused this exception
     */
    public List<UriRouteMatch<Object, Object>> getUriRoutes() {
        return this.uriRoutes;
    }

    private static String buildMessage(String uri, List<UriRouteMatch<Object, Object>> uriRoutes) {
        StringBuilder message = new StringBuilder("More than 1 route matched the incoming request. The following routes matched ");
        message.append(uri).append(": ");
        message.append(uriRoutes
            .stream()
            .map((Object::toString))
            .collect(Collectors.joining(", ")));
        return message.toString();
    }
}

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
package io.micronaut.web.router;

import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpMethod;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The checks of the arguments of the routes declared in code, shared by the route builders, the
 * route assembly and the route declarations.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class RouteArguments {

    private RouteArguments() {
    }

    /**
     * A standard HTTP method of a route: {@link HttpMethod#CUSTOM} stands for any custom method,
     * which a route declares by its name.
     *
     * @param httpMethod The HTTP method
     * @param byName     How to declare the route by the name of the method, for the message
     * @return The HTTP method
     * @throws NullPointerException     if it is {@code null}
     * @throws IllegalArgumentException if it is {@link HttpMethod#CUSTOM}
     */
    public static HttpMethod standardMethod(@Nullable HttpMethod httpMethod, String byName) {
        Objects.requireNonNull(httpMethod, "httpMethod");
        if (httpMethod == HttpMethod.CUSTOM) {
            throw new IllegalArgumentException("HttpMethod.CUSTOM is not the name of a method: declare a route of a custom HTTP method by its name, e.g. " + byName);
        }
        return httpMethod;
    }

    /**
     * The name of the HTTP method of a route: a token, as the method of a request is.
     *
     * @param httpMethodName The name
     * @return The name
     * @throws NullPointerException     if it is {@code null}
     * @throws IllegalArgumentException if it is empty or not a token, e.g. blank
     */
    public static String httpMethodName(@Nullable String httpMethodName) {
        Objects.requireNonNull(httpMethodName, "httpMethodName");
        if (httpMethodName.isEmpty() || !httpMethodName.chars().allMatch(RouteArguments::isTokenChar)) {
            throw new IllegalArgumentException("The name of an HTTP method must be a token, e.g. PROPFIND: '" + httpMethodName + "'");
        }
        return httpMethodName;
    }

    /**
     * @param c A character
     * @return Whether it is a {@code tchar} of RFC 9110
     */
    private static boolean isTokenChar(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
    }

    /**
     * The port of a handler route or of a group of handler routes: a port the server can listen
     * on. Unlike {@code @Controller(port = ...)}, which ignores a negative port, and with
     * {@code 0} opens a listener on a random port that the route cannot know, it is rejected.
     *
     * @param port The port
     * @return The port
     * @throws IllegalArgumentException if it is not between {@code 1} and {@code 65535}
     */
    public static int port(int port) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("The port of a route must be between 1 and 65535: " + port);
        }
        return port;
    }

    /**
     * The port of a handler route or of a group of handler routes given as a string, like
     * {@code @Controller(port = "${my.admin.port}")}: a number, or an expression with property
     * placeholders, with defaults, e.g. {@code ${my.admin.port:8081}}, resolved with the
     * placeholder resolver of the environment like the port of a controller, then checked like
     * {@link #port(int)}.
     *
     * @param port                The port or the expression
     * @param placeholderResolver The placeholder resolver of the environment, or {@code null} if
     *                            there is none, e.g. for a route table built at runtime
     * @return The port
     * @throws io.micronaut.context.exceptions.ConfigurationException if a placeholder cannot be resolved
     * @throws IllegalArgumentException if the port is not a number between {@code 1} and
     *                                  {@code 65535}, or has a placeholder and there is no resolver
     */
    public static int port(String port, @Nullable PropertyPlaceholderResolver placeholderResolver) {
        Objects.requireNonNull(port, "port");
        String resolved;
        if (placeholderResolver != null) {
            resolved = placeholderResolver.resolveRequiredPlaceholders(port);
        } else if (port.contains("${")) {
            throw new IllegalArgumentException("The port of a route has a placeholder, and there is no environment to resolve it with: " + port);
        } else {
            resolved = port;
        }
        int value;
        try {
            value = Integer.parseInt(resolved.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The port of a route is not a number: " + port
                + (resolved.equals(port) ? "" : ", resolved to: " + resolved), e);
        }
        return port(value);
    }

    /**
     * The name of an executor to run a route or a filter on.
     *
     * @param executorName The name
     * @return The name
     * @throws NullPointerException     if it is {@code null}
     * @throws IllegalArgumentException if it is blank
     */
    public static String executorName(@Nullable String executorName) {
        Objects.requireNonNull(executorName, "executorName");
        if (executorName.isBlank()) {
            throw new IllegalArgumentException("The name of an executor must not be blank");
        }
        return executorName;
    }
}

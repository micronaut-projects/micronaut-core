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
package io.micronaut.web.router.spi;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.uri.RouteTemplate;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * The descriptor of a slot of a {@link RoutePlan}: the potential route of one declaration.
 *
 * @param key               The logical key of the declaration: deterministic for the declaration,
 *                          from the namespace of the compiler that described it, the owner, the
 *                          erased method signature, the actual HTTP method and an alias discriminator,
 *                          e.g. {@code controller:example.PetController#show(long)@GET[0]}
 * @param httpMethodName    The actual HTTP method name, the custom name for a custom method
 * @param template          The template with the identifier of its engine
 * @param engineVersion     The version of the engine that parsed the template at compile time
 * @param requiredPrefix    A literal every path the route matches starts with, see {@link io.micronaut.http.uri.ParsedRouteTemplate#requiredPrefix()}
 * @param rawLength         The length of the literal parts, see {@link io.micronaut.http.uri.ParsedRouteTemplate#rawLength()}
 * @param pathVariableCount The number of path variables, see {@link io.micronaut.http.uri.ParsedRouteTemplate#pathVariableCount()}
 * @param captures          The names of the path variables the parser captures, in the order of their spans
 * @param compiled          Whether the parser of the plan matches the slot; otherwise the engine of the template does
 * @param fallbackReason    Why the parser does not match the slot, or {@code null} when it does
 * @param controller        The controller method of the route, or {@code null} for a declaration a handler is bound to
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public record RouteSlot(String key,
                        String httpMethodName,
                        RouteTemplate template,
                        String engineVersion,
                        String requiredPrefix,
                        int rawLength,
                        int pathVariableCount,
                        String[] captures,
                        boolean compiled,
                        @Nullable String fallbackReason,
                        @Nullable ControllerRoute controller) {

    /**
     * @param key               The logical key of the declaration
     * @param httpMethodName    The actual HTTP method name
     * @param template          The template
     * @param engineVersion     The version of the engine of the template
     * @param requiredPrefix    The required path prefix
     * @param rawLength         The length of the literal parts
     * @param pathVariableCount The number of path variables
     * @param captures          The names of the captured path variables
     * @param compiled          Whether the parser matches the slot
     * @param fallbackReason    Why the parser does not match the slot
     * @param controller        The controller method, or {@code null}
     */
    @UsedByGeneratedCode
    public RouteSlot {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(httpMethodName, "httpMethodName");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(engineVersion, "engineVersion");
        Objects.requireNonNull(requiredPrefix, "requiredPrefix");
        Objects.requireNonNull(captures, "captures");
    }

    /**
     * @return The HTTP method, {@link HttpMethod#CUSTOM} for a custom method
     */
    public HttpMethod httpMethod() {
        return HttpMethod.parse(httpMethodName);
    }

    /**
     * Append the canonical form of the descriptor, see {@link RoutePlan#fingerprint(RouteSlot[])}.
     *
     * @param builder The builder
     */
    void appendCanonical(StringBuilder builder) {
        char s = '\u001f';
        builder.append(key).append(s).append(httpMethodName).append(s).append(template.engineId()).append(s)
            .append(template.expression()).append(s).append(engineVersion).append(s).append(requiredPrefix).append(s)
            .append(rawLength).append(s).append(pathVariableCount).append(s).append(String.join(",", captures)).append(s)
            .append(compiled).append(s).append(fallbackReason == null ? "" : fallbackReason);
        if (controller != null) {
            builder.append(s);
            controller.appendCanonical(builder);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RouteSlot other && key.equals(other.key) && httpMethodName.equals(other.httpMethodName)
            && template.equals(other.template) && engineVersion.equals(other.engineVersion) && requiredPrefix.equals(other.requiredPrefix)
            && rawLength == other.rawLength && pathVariableCount == other.pathVariableCount && Arrays.equals(captures, other.captures)
            && compiled == other.compiled && Objects.equals(fallbackReason, other.fallbackReason) && Objects.equals(controller, other.controller);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, httpMethodName, template);
    }

    @Override
    public String toString() {
        return httpMethodName + ' ' + template + " [" + key + (compiled ? "]" : ", runtime: " + fallbackReason + ']');
    }
}

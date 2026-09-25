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
package io.micronaut.web.router.processor;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.web.router.spi.ControllerRoute;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The structured description of a route that a declaration processor contributes to the route
 * compiler, see {@link RoutePlanCompiler}: the logical key of the declaration, its HTTP method,
 * its template with the identifier of its engine, and the element it originates from. The
 * compiler parses the template with its engine and lowers it; a processor never parses templates
 * itself.
 *
 * @param key            The logical key of the declaration, see {@link #key(String, ClassElement, MethodElement, String, int)}
 * @param httpMethodName The actual HTTP method name, the custom name for a custom method
 * @param template       The template
 * @param controller     The controller method of the route, or {@code null} for a declaration a handler is bound to
 * @param origin         The element the route originates from, for diagnostics
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public record RouteDescription(String key,
                               String httpMethodName,
                               RouteTemplate template,
                               @Nullable ControllerRoute controller,
                               Element origin) {

    /**
     * @param key            The logical key of the declaration
     * @param httpMethodName The actual HTTP method name
     * @param template       The template
     * @param controller     The controller method of the route, or {@code null}
     * @param origin         The originating element
     */
    public RouteDescription {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(httpMethodName, "httpMethodName");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(origin, "origin");
    }

    /**
     * The logical key of a declaration: deterministic for the declaration, from the namespace of
     * the processor, the owner, the erased method signature, the actual HTTP method and an alias
     * discriminator, e.g. {@code controller:example.PetController#show(long)@GET[0]}. It does not
     * depend on the order declarations are found in, on hashes or on the machine.
     *
     * @param namespace      The namespace of the processor, e.g. {@code controller}
     * @param owner          The type that declares the route
     * @param method         The method the route is declared by
     * @param httpMethodName The actual HTTP method name
     * @param alias          The position of the template among the templates of the method for the HTTP method
     * @return The key
     */
    public static String key(String namespace, ClassElement owner, MethodElement method, String httpMethodName, int alias) {
        StringBuilder key = new StringBuilder(namespace).append(':').append(owner.getName()).append('#').append(method.getName()).append('(');
        ParameterElement[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                key.append(',');
            }
            key.append(erasedName(parameters[i]));
        }
        return key.append(")@").append(httpMethodName).append('[').append(alias).append(']').toString();
    }

    /**
     * The name of the erased type of a parameter, as {@link Class#getName()} returns it at runtime.
     *
     * @param parameter The parameter
     * @return The name
     */
    public static String erasedName(ParameterElement parameter) {
        ClassElement type = parameter.getType();
        if (!type.isArray()) {
            return type.getName();
        }
        StringBuilder name = new StringBuilder();
        name.append("[".repeat(type.getArrayDimensions()));
        ClassElement component = type.fromArray();
        while (component.isArray()) {
            component = component.fromArray();
        }
        if (component.isPrimitive()) {
            name.append(switch (component.getName()) {
                case "boolean" -> 'Z';
                case "byte" -> 'B';
                case "char" -> 'C';
                case "short" -> 'S';
                case "int" -> 'I';
                case "long" -> 'J';
                case "float" -> 'F';
                case "double" -> 'D';
                default -> throw new IllegalStateException("Unknown primitive type: " + component.getName());
            });
        } else {
            name.append('L').append(component.getName()).append(';');
        }
        return name.toString();
    }
}

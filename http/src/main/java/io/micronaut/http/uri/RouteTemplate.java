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
package io.micronaut.http.uri;

import io.micronaut.core.annotation.Experimental;

import java.util.Objects;

/**
 * A route template: an expression together with the identity of the template language, the
 * {@link io.micronaut.http.uri.spi.RouteTemplateEngine engine}, that gives it its meaning.
 *
 * <p>The same text can mean different things in different languages: {@code /items/{id:3}} is a
 * variable of at most three characters for the {@link #MICRONAUT Micronaut} engine, while another
 * engine may read the modifier as a regular expression. The engine is therefore never inferred from
 * the syntax of the expression, and two templates are equal only if both the engine and the
 * expression are.</p>
 *
 * <p>A template is plain data: creating one does not look up or load the engine. The engine is
 * resolved when routes are assembled.</p>
 *
 * @param engineId   The identifier of the template engine, e.g. {@value #MICRONAUT}
 * @param expression The template expression in the language of the engine
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public record RouteTemplate(String engineId, String expression) {

    /**
     * The identifier of the engine of the Micronaut URI template language, the language of
     * {@link UriMatchTemplate} and of the {@code String} templates of the route APIs.
     */
    public static final String MICRONAUT = "micronaut";

    /**
     * @param engineId   The identifier of the template engine
     * @param expression The template expression
     */
    public RouteTemplate {
        Objects.requireNonNull(engineId, "engineId");
        Objects.requireNonNull(expression, "expression");
        if (engineId.isBlank()) {
            throw new IllegalArgumentException("The engine identifier of a route template must not be blank");
        }
    }

    /**
     * A template in the Micronaut URI template language.
     *
     * @param expression The template, e.g. {@code /pets/{id}}
     * @return The template
     */
    public static RouteTemplate micronaut(String expression) {
        return new RouteTemplate(MICRONAUT, expression);
    }

    /**
     * A template in the language of the given engine.
     *
     * @param engineId   The identifier of the engine
     * @param expression The template expression
     * @return The template
     */
    public static RouteTemplate of(String engineId, String expression) {
        return new RouteTemplate(engineId, expression);
    }

    /**
     * @return Whether the template is in the Micronaut URI template language
     */
    public boolean isMicronaut() {
        return MICRONAUT.equals(engineId);
    }

    @Override
    public String toString() {
        return isMicronaut() ? expression : engineId + ':' + expression;
    }
}

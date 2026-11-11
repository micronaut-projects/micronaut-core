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

/**
 * A route template language: parses the text of a template. A route declared with a parser and
 * the text of its template is parsed once, when the route is added, e.g.
 * {@code routes.route(HttpMethod.GET, parser, "/items/{id}")}; a route declared with a
 * {@link RouteTemplate} uses it as it is.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface RouteTemplateParser {

    /**
     * Parse a template.
     *
     * @param expression The text of the template
     * @return The template
     * @throws IllegalArgumentException if the text is not a template of the language
     */
    RouteTemplate parse(String expression);
}

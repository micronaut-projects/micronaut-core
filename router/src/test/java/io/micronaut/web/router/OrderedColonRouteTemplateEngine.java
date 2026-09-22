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

import io.micronaut.http.uri.ParsedRouteTemplate;
import io.micronaut.http.uri.RouteTemplate;

import java.util.Comparator;
import java.util.Optional;

/**
 * The colon language of {@link ColonRouteTemplateEngine} with the order of specificity of JAX-RS
 * (JAX-RS 3.1, section 3.7.2): more literal text first, then more variables, then more variables
 * with a regular expression.
 */
public class OrderedColonRouteTemplateEngine extends ColonRouteTemplateEngine {

    public static final String ID = "test.colon.ordered";

    static final Comparator<ParsedRouteTemplate> ORDER = Comparator
        .comparingInt(ParsedRouteTemplate::rawLength).reversed()
        .thenComparing(Comparator.comparingInt(ParsedRouteTemplate::pathVariableCount).reversed())
        .thenComparing(Comparator.comparingInt(ParsedRouteTemplate::patternVariableCount).reversed());

    public OrderedColonRouteTemplateEngine() {
        super(ID);
    }

    protected OrderedColonRouteTemplateEngine(String id) {
        super(id);
    }

    public static RouteTemplate template(String expression) {
        return RouteTemplate.of(ID, expression);
    }

    @Override
    public Optional<Comparator<ParsedRouteTemplate>> comparator() {
        return Optional.of(ORDER);
    }
}

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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.uri.ParsedRouteTemplate;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.spi.RouteTemplateEngines;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * An {@link IndexedRouteDeclaration} declared in code with a template of an engine other than the
 * Micronaut one. The keys come from the template the engine parsed, when they are first read.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class EngineRouteDeclaration implements IndexedRouteDeclaration {
    private final HttpMethod httpMethod;
    private final String httpMethodName;
    private final RouteTemplate template;
    private volatile @Nullable ParsedRouteTemplate parsed;

    EngineRouteDeclaration(HttpMethod httpMethod, String httpMethodName, RouteTemplate template) {
        this.httpMethod = Objects.requireNonNull(httpMethod, "httpMethod");
        this.httpMethodName = Objects.requireNonNull(httpMethodName, "httpMethodName");
        this.template = Objects.requireNonNull(template, "template");
    }

    private ParsedRouteTemplate parsed() {
        ParsedRouteTemplate p = parsed;
        if (p == null) {
            p = RouteTemplateEngines.defaults().parse(template);
            parsed = p;
        }
        return p;
    }

    @Override
    public HttpMethod httpMethod() {
        return httpMethod;
    }

    @Override
    public String httpMethodName() {
        return httpMethodName;
    }

    @Override
    public String uriTemplate() {
        return template.expression();
    }

    @Override
    public RouteTemplate template() {
        return template;
    }

    @Override
    public String requiredPathPrefix() {
        return parsed().requiredPrefix();
    }

    @Override
    public int rawLength() {
        return parsed().rawLength();
    }

    @Override
    public int pathVariableCount() {
        return parsed().pathVariableCount();
    }

    @Override
    public int patternVariableCount() {
        return parsed().patternVariableCount();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EngineRouteDeclaration other && httpMethod == other.httpMethod && httpMethodName.equals(other.httpMethodName) && template.equals(other.template);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * httpMethod.hashCode() + httpMethodName.hashCode()) + template.hashCode();
    }

    @Override
    public String toString() {
        return "EngineRouteDeclaration[httpMethod=" + httpMethodName + ", template=" + template + ']';
    }
}

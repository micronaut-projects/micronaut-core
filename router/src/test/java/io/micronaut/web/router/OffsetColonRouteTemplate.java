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

import io.micronaut.http.uri.RouteCaptures;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.RouteTemplateVariable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A template of the {@link ColonRouteTemplateEngine colon language} that reports where its
 * captured values are in the path, see {@link RouteCaptures#start(int)}: one value per segment.
 *
 * @param delegate The colon template
 */
record OffsetColonRouteTemplate(RouteTemplate delegate) implements RouteTemplate {

    /**
     * @param expression The colon expression, e.g. {@code /items/:item}
     * @return The template that reports offsets
     */
    static RouteTemplate template(String expression) {
        return new OffsetColonRouteTemplate(ColonRouteTemplateEngine.template(expression));
    }

    @Override
    public String expression() {
        return delegate.expression();
    }

    @Override
    public List<RouteTemplateVariable> variables() {
        return delegate.variables();
    }

    @Override
    public @Nullable RouteCaptures match(String rawPath) {
        RouteCaptures captures = delegate.match(rawPath);
        if (captures == null) {
            return null;
        }
        String path = captures.path();
        List<String> parts = new ArrayList<>();
        for (String part : delegate.expression().split("/")) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        List<@Nullable String> values = captures.values();
        int[] offsets = new int[2 * values.size()];
        java.util.Arrays.fill(offsets, -1);
        int position = 1;
        int variable = 0;
        for (String part : parts) {
            if (position > path.length()) {
                break;
            }
            int next = path.indexOf('/', position);
            int end = next < 0 ? path.length() : next;
            if (part.startsWith(":")) {
                if (values.get(variable) != null) {
                    offsets[2 * variable] = position;
                    offsets[2 * variable + 1] = end;
                }
                variable++;
            }
            position = end + 1;
        }
        return RouteCaptures.of(path, captures.variables(), values, offsets);
    }

    @Override
    public RouteTemplate mount(String prefix) {
        return new OffsetColonRouteTemplate(delegate.mount(prefix));
    }

    @Override
    public String requiredPrefix() {
        return delegate.requiredPrefix();
    }

    @Override
    public int rawLength() {
        return delegate.rawLength();
    }

    @Override
    public int pathVariableCount() {
        return delegate.pathVariableCount();
    }

    @Override
    public int patternVariableCount() {
        return delegate.patternVariableCount();
    }
}

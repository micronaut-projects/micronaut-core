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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.uri.MicronautRouteTemplateEngine;
import io.micronaut.http.uri.ParsedRouteTemplate;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.RouteTemplateSegment;
import io.micronaut.http.uri.RouteTemplateVariable;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplateMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowers the templates of the Micronaut engine that its matcher matches segment by segment: the
 * literal segments and the whole-segment {@code {name}} variables of
 * {@link UriTemplateMatcher#exactPathSegments()}, with the rule of such a variable,
 * {@link UriTemplateMatcher#acceptsSegmentVariable(String, int, int)}. The structure comes from
 * the matcher the router uses, not from the text of the template.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class MicronautRouteTemplateCompiler implements RouteTemplateCompiler {

    @Override
    public String engineId() {
        return RouteTemplate.MICRONAUT;
    }

    @Override
    public String engineVersion() {
        return MicronautRouteTemplateEngine.VERSION;
    }

    @Override
    public LoweredTemplate lower(ParsedRouteTemplate template) {
        UriMatchTemplate uriMatchTemplate = MicronautRouteTemplateEngine.uriMatchTemplate(template);
        if (uriMatchTemplate == null) {
            return LoweredTemplate.unsupported("not a template of the Micronaut engine");
        }
        // the matcher of the route, as MicronautRouteTemplateEngine prepares it
        List<RouteTemplateSegment> segments = new UriTemplateMatcher(uriMatchTemplate.getTemplateString()).exactPathSegments();
        if (segments == null) {
            return LoweredTemplate.unsupported("the template is not made of literal segments and whole-segment variables");
        }
        List<String> captures = new ArrayList<>();
        for (RouteTemplateVariable variable : template.variables()) {
            if (variable.location() == RouteTemplateVariable.Location.PATH) {
                captures.add(variable.name());
            }
        }
        List<LoweredTemplate.Segment> lowered = new ArrayList<>(segments.size());
        for (RouteTemplateSegment segment : segments) {
            lowered.add(segment.kind() == RouteTemplateSegment.Kind.LITERAL
                ? LoweredTemplate.Segment.literal(segment.literal())
                : LoweredTemplate.Segment.variable(LoweredTemplate.VariableRule.MICRONAUT));
        }
        if (lowered.stream().filter(LoweredTemplate.Segment::isVariable).count() != captures.size()) {
            return LoweredTemplate.unsupported("the path variables are not the variable segments");
        }
        return LoweredTemplate.of(lowered, captures);
    }
}

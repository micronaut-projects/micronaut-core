/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.validation.routes;

import io.micronaut.context.env.DefaultPropertyPlaceholderResolver;
import io.micronaut.context.env.DefaultPropertyPlaceholderResolver.*;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.convert.DefaultConversionService;
import io.micronaut.http.annotation.HttpMethodMapping;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.validation.routes.rules.MissingParameterRule;
import io.micronaut.validation.routes.rules.NullableParameterRule;
import io.micronaut.validation.routes.rules.RouteValidationRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Visits methods annotated with {@link HttpMethodMapping} and validates the
 * parameters are consistent with the URI.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class RouteValidationVisitor implements TypeElementVisitor<Object, HttpMethodMapping> {

    private List<RouteValidationRule> rules = new ArrayList<>();
    private boolean skipValidation = false;
    private final DefaultPropertyPlaceholderResolver resolver = new DefaultPropertyPlaceholderResolver(null, new DefaultConversionService());

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (skipValidation) {
            return;
        }
        AnnotationValue<HttpMethodMapping> mappingAnnotation = element.getAnnotation(HttpMethodMapping.class);
        if (mappingAnnotation != null) {
            String uri = mappingAnnotation.getRequiredValue(String.class);

            List<Segment> segments = resolver.buildSegments(uri);
            StringBuilder uriValue = new StringBuilder();
            for (Segment segment: segments) {
                if (segment instanceof RawSegment) {
                    uriValue.append(segment.getValue(String.class));
                } else {
                    uriValue.append("tmp");
                }
            }

            UriMatchTemplate template = UriMatchTemplate.of(uriValue.toString());
            RouteParameterElement[] parameters = Arrays.stream(element.getParameters())
                    .map(RouteParameterElement::new)
                    .toArray(RouteParameterElement[]::new);

            for (RouteValidationRule rule: rules) {
                RouteValidationResult result = rule.validate(template, parameters);

                if (!result.isValid()) {
                    for (String err: result.getErrorMessages()) {
                        context.fail(err, element);
                    }
                }
            }
        }
    }

    @Override
    public void start(VisitorContext visitorContext) {
        String prop = System.getProperty("micronaut.route.validation");
        skipValidation = prop != null && prop.equals("false");
        rules.add(new MissingParameterRule());
        rules.add(new NullableParameterRule());
    }
}

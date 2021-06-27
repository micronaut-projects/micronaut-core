/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.validation.routes;

import io.micronaut.context.env.DefaultPropertyPlaceholderResolver;
import io.micronaut.context.env.DefaultPropertyPlaceholderResolver.RawSegment;
import io.micronaut.context.env.DefaultPropertyPlaceholderResolver.Segment;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.DefaultConversionService;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.validation.routes.rules.MissingParameterRule;
import io.micronaut.validation.routes.rules.NullableParameterRule;
import io.micronaut.validation.routes.rules.RequestBeanParameterRule;
import io.micronaut.validation.routes.rules.RouteValidationRule;

import javax.annotation.processing.SupportedOptions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Visits methods annotated with HttpMethodMapping and validates the
 * parameters are consistent with the URI.
 *
 * @author James Kleeh
 * @since 1.0
 */
@SupportedOptions(RouteValidationVisitor.VALIDATION_OPTION)
public class RouteValidationVisitor implements TypeElementVisitor<Object, Object> {

    static final String MICRONAUT_PROCESSING_INCREMENTAL = "micronaut.processing.incremental";
    static final String VALIDATION_OPTION = "micronaut.route.validation";
    private static final String METHOD_MAPPING_ANN = "io.micronaut.http.annotation.HttpMethodMapping";
    private List<RouteValidationRule> rules = new ArrayList<>();
    private boolean skipValidation = false;
    private final DefaultPropertyPlaceholderResolver resolver = new DefaultPropertyPlaceholderResolver(null, new DefaultConversionService());

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return CollectionUtils.setOf(
                "io.micronaut.http.annotation.Controller",
                "io.micronaut.http.client.annotation.Client",
                METHOD_MAPPING_ANN);
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (skipValidation) {
            return;
        }

        AnnotationValue<?> mappingAnnotation = element.getAnnotation(METHOD_MAPPING_ANN);
        if (mappingAnnotation != null) {
            Set<String> uris = CollectionUtils.setOf(mappingAnnotation.stringValues("uris"));
            mappingAnnotation.stringValue().ifPresent(uris::add);

            List<UriMatchTemplate> templates = uris.stream().map(uri -> {
                List<Segment> segments = resolver.buildSegments(uri);
                StringBuilder uriValue = new StringBuilder();
                for (Segment segment : segments) {
                    if (segment instanceof RawSegment) {
                        uriValue.append(segment.getValue(String.class));
                    } else {
                        uriValue.append("tmp");
                    }
                }

                return UriMatchTemplate.of(uriValue.toString());
            }).collect(Collectors.toList());

            RouteParameterElement[] parameters = Arrays.stream(element.getParameters())
                    .map(RouteParameterElement::new)
                    .toArray(RouteParameterElement[]::new);

            for (RouteValidationRule rule : rules) {
                RouteValidationResult result = rule.validate(templates, parameters, element);

                if (!result.isValid()) {
                    for (String err : result.getErrorMessages()) {
                        context.fail(err, element);
                    }
                }
            }
        }
    }

    @Override
    public void start(VisitorContext visitorContext) {
        skipValidation = shouldSkipRouteValidation(visitorContext);
        rules.add(new MissingParameterRule());
        rules.add(new NullableParameterRule());
        rules.add(new RequestBeanParameterRule());
    }

    /**
     * Check whether to skip route validation.
     * <p>
     * Route validation is disabled when using Java 8 or below with incremental compilation. The
     * Java 8 compiler does not load parameter names for compiled classes, so in this case it is
     * impossible to match route parameters to method parameters.
     *
     * @param visitorContext The visitor context
     * @return A boolean indicating whether to skip route validation.
     */
    private static boolean shouldSkipRouteValidation(VisitorContext visitorContext) {
        int javaVersion = getVersion();
        if (javaVersion < 9) {
            String incremental = visitorContext.getOptions().get(MICRONAUT_PROCESSING_INCREMENTAL);
            if (incremental != null && incremental.equals("true")) {
                return true;
            }
        }
        String prop = visitorContext.getOptions().getOrDefault(VALIDATION_OPTION, "true");
        return prop != null && prop.equals("false");
    }

    private static int getVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}

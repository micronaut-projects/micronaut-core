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
package io.micronaut.validation.routes.rules;

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.validation.routes.RouteValidationResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING_ARRAY;

/**
 * Validates all route uri variables are present in the route arguments.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class MissingParameterRule implements RouteValidationRule {

    @Override
    public RouteValidationResult validate(List<UriMatchTemplate> templates, ParameterElement[] parameters, MethodElement method) {

        Set<String> variables = templates.stream().flatMap(t -> t.getVariableNames().stream()).collect(Collectors.toSet());
        Set<String> routeVariables = Arrays.stream(parameters).map(ParameterElement::getName).collect(Collectors.toCollection(LinkedHashSet::new));

        for (ParameterElement parameter : parameters) {
            if (parameter.hasAnnotation("io.micronaut.http.annotation.Body")) {
                for (AnnotatedElement element : findProperties(parameter.getType())) {
                    routeVariables.add(element.getName());
                }
            }
            if (parameter.hasAnnotation("io.micronaut.http.annotation.RequestBean")) {
                for (AnnotatedElement element : findProperties(parameter.getType())) {
                    if (element.getAnnotationMetadata().hasStereotype(Bindable.class)) {
                        routeVariables.add(element.getAnnotationMetadata().stringValue(Bindable.class).orElse(element.getName()));
                    }
                }
            }
        }

        List<String> errorMessages = new ArrayList<>();

        for (String v: variables) {
            if (!routeVariables.contains(v)) {
                errorMessages.add("The route declares a uri variable named [%s], but no corresponding method argument is present".formatted(v));
            }
        }

        return new RouteValidationResult(errorMessages.toArray(EMPTY_STRING_ARRAY));
    }

    private static Collection<? extends AnnotatedElement> findProperties(ClassElement t) {
        if (t.isRecord()) {
            Optional<MethodElement> primaryConstructor = t.getPrimaryConstructor();
            if (primaryConstructor.isPresent()) {
                return Arrays.asList(primaryConstructor.get().getParameters());
            }
        }
        return t.getBeanProperties();
    }
}

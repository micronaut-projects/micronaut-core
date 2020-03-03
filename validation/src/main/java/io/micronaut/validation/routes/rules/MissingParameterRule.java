/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.validation.routes.rules;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.validation.routes.RouteValidationResult;

import java.util.*;
import java.util.stream.Collectors;

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
        List<String> routeVariables = Arrays.stream(parameters).map(ParameterElement::getName).collect(Collectors.toList());

        routeVariables.addAll(Arrays.stream(parameters)
                .filter(p -> p.hasAnnotation(Body.class))
                .map(ParameterElement::getType)
                .filter(Objects::nonNull)
                .flatMap(t -> t.getBeanProperties().stream())
                .map(PropertyElement::getName)
                .collect(Collectors.toList()));

        List<String> errorMessages = new ArrayList<>();

        for (String v: variables) {
            if (!routeVariables.contains(v)) {
                errorMessages.add(String.format("The route declares a uri variable named [%s], but no corresponding method argument is present", v));
            }
        }

        return new RouteValidationResult(errorMessages.toArray(new String[0]));
    }

}

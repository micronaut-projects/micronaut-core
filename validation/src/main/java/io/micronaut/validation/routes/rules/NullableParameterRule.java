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

import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriMatchVariable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.validation.routes.RouteValidationResult;

import java.util.*;

/**
 * Validates route parameters are nullable or optional for optional
 * template variables.
 *
 * @author James Kleeh
 * @author graemerocher
 * @since 1.0
 */
public class NullableParameterRule implements RouteValidationRule {

    @Override
    public RouteValidationResult validate(List<UriMatchTemplate> templates, ParameterElement[] parameters, MethodElement method) {
        List<String> errorMessages = new ArrayList<>();

        boolean isClient = method.hasAnnotation("io.micronaut.http.client.annotation.Client");

        //Optional variables can be required in clients
        if (!isClient) {
            Map<String, UriMatchVariable> variables = new HashMap<>();
            Set<UriMatchVariable> required = new HashSet<>();
            for (UriMatchTemplate template: templates) {
                for (UriMatchVariable variable: template.getVariables()) {
                    if (!variable.isOptional() || variable.isExploded()) {
                        required.add(variable);
                    }
                    variables.compute(variable.getName(), (key, var) -> {
                        if (var == null) {
                            if (variable.isOptional() && !variable.isExploded()) {
                                return variable;
                            } else {
                                return null;
                            }
                        } else {
                            if (!var.isOptional() || var.isExploded()) {
                                if (variable.isOptional() && !variable.isExploded()) {
                                    return variable;
                                } else {
                                    return var;
                                }
                            } else {
                                return var;
                            }
                        }
                    });
                }
            }

            for (UriMatchVariable variable: required) {
                if (templates.stream().anyMatch(t -> !t.getVariableNames().contains(variable.getName()))) {
                    variables.putIfAbsent(variable.getName(), variable);
                }
            }

            for (UriMatchVariable variable : variables.values()) {
                Arrays.stream(parameters)
                        .filter(p -> p.getName().equals(variable.getName()))
                        .findFirst()
                        .ifPresent(p -> {
                            ClassElement type = p.getType();
                            boolean hasDefaultValue = p.findAnnotation(Bindable.class).flatMap(av -> av.stringValue("defaultValue")).isPresent();
                            if (!isNullable(p) && type != null && !type.isAssignable(Optional.class) && !hasDefaultValue) {
                                errorMessages.add(String.format("The uri variable [%s] is optional, but the corresponding method argument [%s] is not defined as an Optional or annotated with the javax.annotation.Nullable annotation.", variable.getName(), p.toString()));
                            }
                        });

            }
        }

        return new RouteValidationResult(errorMessages.toArray(new String[0]));
    }

    private boolean isNullable(ParameterElement p) {
        // Handles javax.annotation.Nullable or org.jetbrains.annotations.Nullable or Spring's version
        return p.getAnnotationNames().stream().anyMatch(n -> NameUtils.getSimpleName(n).equals("Nullable"));
    }

}

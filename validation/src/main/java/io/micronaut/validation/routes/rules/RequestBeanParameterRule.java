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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.RequestBean;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.validation.routes.RouteValidationResult;

/**
 * Validates RequestBean parameters
 *
 * @author Anze Sodja
 * @since 2.0
 */
public class RequestBeanParameterRule implements RouteValidationRule {

    @Override
    public RouteValidationResult validate(List<UriMatchTemplate> templates, ParameterElement[] parameters, MethodElement method) {
        return new RouteValidationResult(Arrays.stream(parameters)
                .filter(p -> p.hasAnnotation(RequestBean.class))
                .flatMap(p -> validate(p).stream())
                .toArray(String[]::new));
    }

    private List<String> validate(ParameterElement parameterElement) {
        List<String> errors = new ArrayList<>();
        List<PropertyElement> bindableProperties = parameterElement.getType().getBeanProperties().stream()
                .filter(p -> p.hasStereotype(Bindable.class) || p.getType().isAssignable(HttpRequest.class))
                .collect(Collectors.toList());
        Optional<MethodElement> primaryConstructor = parameterElement.getType().getPrimaryConstructor();

        if (primaryConstructor.isPresent() && primaryConstructor.get().getParameters().length > 0) {
            // @Creator constructor
            List<ParameterElement> constructorParameters = Arrays.asList(primaryConstructor.get().getParameters());

            // Check no constructor parameter has any @Bindable annotation
            // We could allow this, but this would add some complexity, some annotations that can be used in combination
            // with @Bindable works only on fields (e.g. bean validation annotations) and this might confuse Micronaut users
            constructorParameters.stream()
                    .filter(p -> p.hasStereotype(Bindable.class))
                    .forEach(p -> errors.add("Parameter of Primary Constructor (or @Creator Method) [" + p.getName() + "] for type ["
                            + parameterElement.getType().getName() + "] has one of @Bindable annotations. This is not supported."
                            + "\nNote1: Primary constructor is a constructor that have parameters or is annotated with @Creator."
                            + "\nNote2: In case you have multiple @Creator constructors, first is used as primary constructor."));

            // Check readonly bindable properties can be set via constructor
            bindableProperties.stream()
                    .filter(PropertyElement::isReadOnly)
                    .filter(p -> constructorParameters.stream().noneMatch(constructorProperty -> constructorProperty.getName().equals(p.getName())))
                    .forEach(p -> errors.add(
                            "Primary Constructor or @Creator Method for Bindable property [" + p.getName() + "] for type ["
                                    + parameterElement.getType().getName() + "] found, but there is no constructor/method parameter with name equal to [" + p.getName() + "]."
                                    + "\nAdd parameter with name [" + p.getName() + "] to your @Creator."
                                    + "\nNote1: Primary constructor is a constructor that have parameters or is annotated with @Creator."
                                    + "\nNote2: In case you have multiple @Creator constructors, first is used as primary constructor."));
        } else {
            // Check readonly bindable properties
            bindableProperties.stream()
                    .filter(PropertyElement::isReadOnly)
                    .forEach(p -> errors.add("Bindable property [" + p.getName()  + "] for type [" + parameterElement.getType().getName() + "]"
                            + " is Read only and cannot be set during initialization.\n"
                            + "Add property setter or add @Creator constructor/method."));
        }
        return errors;
    }

}

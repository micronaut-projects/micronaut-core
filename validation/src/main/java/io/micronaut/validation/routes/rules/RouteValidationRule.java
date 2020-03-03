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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.validation.routes.RouteValidationResult;

import java.util.Collections;
import java.util.List;

/**
 * Describes a rule to validate a route.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
public interface RouteValidationRule {

    /**
     * Validate the given uri template and route arguments.
     *
     * @param template The URI match templates
     * @param parameters The route parameters
     * @param method  The route method
     * @return A {@link RouteValidationResult}
     */
    default RouteValidationResult validate(UriMatchTemplate template, ParameterElement[] parameters, MethodElement method) {
        return validate(Collections.singletonList(template), parameters, method);
    }

    /**
     * Validate the given uri templates and route arguments.
     *
     * @param templates The URI match templates
     * @param parameters The route parameters
     * @param method  The route method
     * @return A {@link RouteValidationResult}
     */
    RouteValidationResult validate(List<UriMatchTemplate> templates, ParameterElement[] parameters, MethodElement method);

}

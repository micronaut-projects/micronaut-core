/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.validation.routes.RouteValidationResult;

import java.util.Arrays;
import java.util.List;

/**
 * Validates that disallowed types are not used as return types in declarative client methods.
 *
 * @author Sergey Gavrilov
 * @since 3.2.0
 */
public class ClientReturnTypeRule implements RouteValidationRule {

    private static final Class<?>[] DISALLOWED_RETURN_TYPES = new Class<?>[]{
            StreamedFile.class,
            SystemFile.class
    };

    @Override
    public RouteValidationResult validate(List<UriMatchTemplate> templates, ParameterElement[] parameters, MethodElement method) {
        String[] errors = new String[]{};
        if (method.getAnnotation(Client.class) != null) {
            ClassElement returnType = method.getReturnType();
            errors = Arrays.stream(DISALLOWED_RETURN_TYPES)
                        .filter(returnType::isAssignable)
                        .map(type -> "Type [" + type + "] and its subtypes must not be used as return types in declarative client methods")
                        .toArray(String[]::new);
        }
        return new RouteValidationResult(errors);
    }
}
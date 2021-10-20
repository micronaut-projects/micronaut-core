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
import java.util.stream.Stream;

/**
 * Validates that disallowed types are not used in declarative client methods.
 *
 * @author Sergey Gavrilov
 * @since 3.2.0
 */
public class ClientTypesRule implements RouteValidationRule {

    /**
     * Types which are not allowed to be used neither as method parameters not as return types.
     */
    private static final Class<?>[] DISALLOWED_TYPES = new Class<?>[]{
            StreamedFile.class,
            SystemFile.class
    };

    /**
     * Types which are not allowed to be used as method parameters.
     */
    private static final Class<?>[] DISALLOWED_PARAMETER_TYPES = new Class<?>[]{
            io.micronaut.http.server.multipart.MultipartBody.class
    };

    @Override
    public RouteValidationResult validate(List<UriMatchTemplate> templates, ParameterElement[] parameters, MethodElement method) {
        String[] errors = new String[]{};
        if (method.getAnnotation(Client.class) != null) {
            errors = Stream.concat(validateReturnType(method), validateParameters(parameters))
                    .toArray(String[]::new);
        }
        return new RouteValidationResult(errors);
    }

    private Stream<String> validateReturnType(MethodElement method) {
        ClassElement returnType = method.getReturnType();
        return Arrays.stream(DISALLOWED_TYPES)
                .filter(returnType::isAssignable)
                .map(type -> "Type [" + type + "] and its subtypes must not be used as return types in declarative client methods");
    }

    private Stream<String> validateParameters(ParameterElement[] parameters) {
        return Stream.concat(Arrays.stream(DISALLOWED_TYPES), Arrays.stream(DISALLOWED_PARAMETER_TYPES))
                .flatMap(type -> validateParametersAgainstType(parameters, type));
    }

    private Stream<String> validateParametersAgainstType(ParameterElement[] parameters, Class<?> type) {
        return Arrays.stream(parameters)
                .map(ParameterElement::getType)
                .filter(parameterType -> parameterType.isAssignable(type))
                .map(parameterType -> "Type [" + type + "] and its subtypes must not be used as client method parameters. " +
                        "Use [" + io.micronaut.http.client.multipart.MultipartBody.class + "] instead");
    }
}
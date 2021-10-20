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
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.validation.routes.RouteValidationResult;

import java.util.List;
import java.util.stream.Stream;

/**
 * Validates that disallowed types are not used in declarative client methods.
 *
 * @author Sergey Gavrilov
 * @author James Kleeh
 * @since 3.2.0
 */
public class ClientTypesRule implements RouteValidationRule {

    /**
     * Types which are not allowed to be used in the context of a declarative client.
     */
    private static final Class<?>[] SERVER_TYPES = new Class<?>[]{
            StreamedFile.class,
            SystemFile.class,
            MultipartBody.class
    };

    @Override
    public RouteValidationResult validate(List<UriMatchTemplate> templates, ParameterElement[] parameters, MethodElement method) {
        String[] errors = new String[]{};
        if (method.hasAnnotation(Client.class)) {
            final Stream.Builder<ClassElement> builder = Stream.<ClassElement>builder().add(method.getReturnType());
            for (ParameterElement param: method.getParameters()) {
                builder.add(param.getType());
            }
            errors = builder.build()
                    .filter(type -> {
                        for (Class<?> clazz: SERVER_TYPES) {
                            if (type.isAssignable(clazz)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .map(type -> "The type [" + type + "] must not be used in declarative client methods. The type is specific to server based usages.")
                    .toArray(String[]::new);
        }
        return new RouteValidationResult(errors);
    }
}

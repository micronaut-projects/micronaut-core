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

import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.validation.routes.RouteValidationResult;

import java.util.List;
import java.util.concurrent.CompletionStage;

import org.reactivestreams.Publisher;

/**
 * Validates that suspended route methods do not declare async or reactive return types.
 */
public final class SuspendedReactiveReturnTypeRule implements RouteValidationRule {

    private static final String MESSAGE = "Unsupported suspended controller return type [%s]. Suspend functions must not return reactive or async types.";

    @Override
    public RouteValidationResult validate(List<UriMatchTemplate> templates, ParameterElement[] parameters, MethodElement method) {
        if (!method.isSuspend()) {
            return new RouteValidationResult(new String[0]);
        }
        ClassElement returnType = method.getReturnType();
        if (returnType.isAssignable(Publisher.class) || returnType.isAssignable(CompletionStage.class) || returnType.getName().equals("kotlinx.coroutines.flow.Flow")) {
            return new RouteValidationResult(new String[]{MESSAGE.formatted(returnType.getName())});
        }
        return new RouteValidationResult(new String[0]);
    }
}

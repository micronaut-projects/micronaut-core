/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.expressions.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.TypedElement;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents expression evaluation context for concrete method. Method's expression
 * evaluation context allows referencing method parameters by name in evaluated
 * expressions.
 *
 * @param methodElement method for which evaluation context is built.
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public record MethodExpressionEvaluationContext(@NonNull MethodElement methodElement) implements ExpressionEvaluationContext {

    @Override
    public List<? extends TypedElement> getTypedElements(String name) {
        return Arrays.stream(methodElement.getParameters())
                   .filter(parameter -> parameter.getName().equals(name))
                   .toList();
    }

    @Override
    public List<MethodElement> getMethods(String name) {
        return Collections.emptyList();
    }
}

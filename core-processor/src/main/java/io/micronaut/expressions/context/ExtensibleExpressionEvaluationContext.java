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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

/**
 * Expression compilation context that can be extended with extra elements.
 *
 * @since 4.0.0
 * @author Sergey Gavrilov
 */
@Internal
public interface ExtensibleExpressionEvaluationContext extends ExpressionEvaluationContext {

    /**
     * @param classElement The type that represents this.
     * @return extended context
     */
    ExtensibleExpressionEvaluationContext withThis(@NonNull ClassElement classElement);

    /**
     * Extends compilation context with method element. Compilation context can only include
     * one method at the same time, so this method will return the context which will
     * replace previous context method element if it was set.
     *
     * @param methodElement extending method
     * @return extended context
     */
    @NonNull
    ExtensibleExpressionEvaluationContext extendWith(@NonNull MethodElement methodElement);

    /**
     * Extends compilation context with class element. Compilation context can include
     * multiple class elements at the same time.
     *
     * @param classElement extending class
     * @return extended context
     */
    @NonNull
    ExtensibleExpressionEvaluationContext extendWith(@NonNull ClassElement classElement);

}

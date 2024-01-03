/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * Factory interface for producing expression evaluation context.
 */
@Experimental
public interface ExpressionCompilationContextFactory {
    /**
     * Builds expression evaluation context for method. Expression evaluation context
     * for method allows referencing method parameter names in evaluated expressions.
     *
     * @param expression    expression reference
     * @param methodElement annotated method
     * @return evaluation context for method
     */
    @NonNull
    ExpressionEvaluationContext buildContextForMethod(@NonNull EvaluatedExpressionReference expression,
                                                      @NonNull MethodElement methodElement);

    /**
     * Builds expression evaluation context for expression reference.
     *
     * @param expression  expression reference
     * @param thisElement
     * @return evaluation context for method
     */
    @NonNull
    ExpressionEvaluationContext buildContext(EvaluatedExpressionReference expression, @Nullable ClassElement thisElement);

    /**
     * Adds evaluated expression context class element to context loader
     * at compilation time.
     *
     * <p>This method should be invoked from the {@link io.micronaut.inject.visitor.TypeElementVisitor#start(VisitorContext)} of a {@link io.micronaut.inject.visitor.TypeElementVisitor}</p>
     *
     * @param contextClass context class element
     * @return This context factory
     */
    @NonNull
    ExpressionCompilationContextFactory registerContextClass(@NonNull ClassElement contextClass);

}

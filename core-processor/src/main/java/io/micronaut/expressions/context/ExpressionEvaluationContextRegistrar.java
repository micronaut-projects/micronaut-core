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

import io.micronaut.context.annotation.AnnotationExpressionContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * Custom type that simplifies registering a new context class.
 *
 * <p>A {@code META-INF/services/io.micronaut.inject.visitor.TypeElementVisitor} should be created for any new implementations.</p>
 *
 * @since 4.0.0
 */
@Experimental
public interface ExpressionEvaluationContextRegistrar extends TypeElementVisitor<AnnotationExpressionContext, AnnotationExpressionContext> {
    @Override
    default void start(VisitorContext visitorContext) {
        visitorContext.getClassElement(getContextClassName())
            .ifPresent(contextClass ->
                visitorContext.getExpressionCompilationContextFactory()
                    .registerContextClass(contextClass)
            );
    }

    String getContextClassName();

    @Override
    default @NonNull VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}

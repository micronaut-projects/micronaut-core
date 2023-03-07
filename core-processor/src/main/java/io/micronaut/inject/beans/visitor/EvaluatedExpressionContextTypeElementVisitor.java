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
package io.micronaut.inject.beans.visitor;

import io.micronaut.context.annotation.EvaluatedExpressionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.expressions.context.ExpressionCompilationContextRegistry;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.Set;

/**
 * A {@link TypeElementVisitor} that visits classes annotated with
 * {@link EvaluatedExpressionContext} and adds discovered context classes to
 * {@link ExpressionCompilationContextRegistry}.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class EvaluatedExpressionContextTypeElementVisitor implements TypeElementVisitor<Object, Object> {

    @Override
    public void start(VisitorContext visitorContext) {
        ExpressionCompilationContextRegistry.reset();
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Collections.singleton(EvaluatedExpressionContext.class.getName());
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!element.isPrivate() && element.hasStereotype(EvaluatedExpressionContext.class)) {
            ExpressionCompilationContextRegistry.registerContextClass(element);
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}

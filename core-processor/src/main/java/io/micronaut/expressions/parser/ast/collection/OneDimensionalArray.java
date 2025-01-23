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
package io.micronaut.expressions.parser.ast.collection;

import io.micronaut.core.annotation.Internal;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.ast.types.TypeIdentifier;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.sourcegen.model.ExpressionDef;

import java.util.List;

import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.getRequiredClassElement;

/**
 * Expression AST node for array instantiation. This node is not used when
 * parsing user's expressions as array instantiation is not supported in
 * evaluated expressions. That's why it doesn't support multidimensional arrays,
 * and the presence of initializer is assumed. It is designed for concrete use-cases,
 * such as wrapping varargs method arguments and building compound evaluated expressions.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class OneDimensionalArray extends ExpressionNode {
    private final TypeIdentifier elementTypeIdentifier;
    private final List<ExpressionNode> initializer;

    public OneDimensionalArray(TypeIdentifier elementTypeIdentifier,
                               List<ExpressionNode> initializer) {
        this.elementTypeIdentifier = elementTypeIdentifier;
        this.initializer = initializer;
    }

    @Override
    public ExpressionDef generateExpression(ExpressionCompilationContext ctx) {
        return elementTypeIdentifier.resolveType(ctx)
            .array()
            .instantiate(
                initializer.stream().map(e -> e.compile(ctx)).toList()
            );
    }

    @Override
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        return getRequiredClassElement(elementTypeIdentifier.resolveType(ctx), ctx.visitorContext())
                    .toArray();
    }

}

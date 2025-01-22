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
package io.micronaut.expressions.parser.ast.operator.unary;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isNumeric;

/**
 * Expression node for unary '-' operator.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class NegOperator extends UnaryOperator {
    public NegOperator(ExpressionNode operand) {
        super(operand);
    }

    @Override
    public TypeDef doResolveType(@NonNull ExpressionVisitorContext ctx) {
        TypeDef nodeType = super.doResolveType(ctx);
        if (!isNumeric(nodeType)) {
            throw new ExpressionCompilationException(
                "Invalid unary '-' operation. Unary '-' can only be applied to numeric types");
        }
        return nodeType;
    }

    @Override
    public ExpressionDef generateExpression(ExpressionCompilationContext ctx) {
        return operand.compile(ctx).math(ExpressionDef.MathUnaryOperation.OpType.NEGATE);
    }
}

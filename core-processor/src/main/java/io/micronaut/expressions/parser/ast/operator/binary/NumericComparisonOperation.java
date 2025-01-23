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
package io.micronaut.expressions.parser.ast.operator.binary;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.BOOLEAN;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isNumeric;

/**
 * Expression AST node for relational operations ({@literal >}, {@literal <}, {@code >=}, {@code <=}) on
 * numeric types.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class NumericComparisonOperation extends ExpressionNode {

    private final ExpressionNode leftOperand;
    private final ExpressionNode rightOperand;
    private final ExpressionDef.ComparisonOperation.OpType type;

    public NumericComparisonOperation(ExpressionNode leftOperand,
                                      ExpressionNode rightOperand,
                                      ExpressionDef.ComparisonOperation.OpType type) {
        this.leftOperand = leftOperand;
        this.rightOperand = rightOperand;
        this.type = type;
    }

    @Override
    protected TypeDef doResolveType(@NonNull ExpressionVisitorContext ctx) {
        TypeDef leftType = leftOperand.resolveType(ctx);
        TypeDef rightType = rightOperand.resolveType(ctx);

        if (!isNumeric(leftType) || !isNumeric(rightType)) {
            throw new ExpressionCompilationException(
                "Numeric comparison operation can only be applied to numeric types");
        }

        return BOOLEAN;
    }

    @Override
    public ExpressionDef generateExpression(ExpressionCompilationContext ctx) {
        return leftOperand.compile(ctx)
            .compare(type, rightOperand.compile(ctx));
    }
}

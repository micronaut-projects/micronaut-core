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
package io.micronaut.expressions.parser.ast.operator.binary;

import io.micronaut.core.annotation.Internal;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.JavaIdioms;
import io.micronaut.sourcegen.model.TypeDef;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.STRING;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.computeNumericOperationTargetType;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isNumeric;
import static io.micronaut.sourcegen.model.ExpressionDef.MathBinaryOperation.OpType.ADDITION;

/**
 * Expression node for binary '+' operator. Works both for math operation and string
 * concatenation.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class AddOperator extends BinaryOperator {

    public AddOperator(ExpressionNode leftOperand, ExpressionNode rightOperand) {
        super(leftOperand, rightOperand);
    }

    @Override
    protected TypeDef resolveOperationType(TypeDef leftOperandType, TypeDef rightOperandType) {
        if (!(leftOperandType.equals(STRING)
            || rightOperandType.equals(STRING)
            || (isNumeric(leftOperandType) && isNumeric(rightOperandType)))) {
            throw new ExpressionCompilationException(
                "'+' operation can only be applied to numeric and string types");
        }

        if (leftOperandType.equals(STRING)
            || rightOperandType.equals(STRING)) {
            return STRING;
        }

        return computeNumericOperationTargetType(leftOperandType, rightOperandType);
    }

    @Override
    public ExpressionDef generateExpression(ExpressionCompilationContext ctx) {
        TypeDef leftType = leftOperand.resolveType(ctx);
        TypeDef rightType = rightOperand.resolveType(ctx);

        if (leftType.equals(STRING) || (rightType.equals(STRING))) {
            return JavaIdioms.concatStrings(
                leftOperand.compile(ctx),
                rightOperand.compile(ctx)
            );
        }
        TypeDef targetType = resolveType(ctx);
        return leftOperand.compile(ctx)
            .cast(targetType)
            .math(ADDITION, rightOperand.compile(ctx).cast(targetType))
            .cast(targetType);
    }
}

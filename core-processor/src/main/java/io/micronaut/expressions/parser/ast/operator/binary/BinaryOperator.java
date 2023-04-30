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
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import org.objectweb.asm.Type;

/**
 * Abstract expression AST node for binary operators.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public abstract sealed class BinaryOperator extends ExpressionNode permits AddOperator,
                                                                           EqOperator,
                                                                           LogicalOperator,
                                                                           MathOperator,
                                                                           PowOperator {
    protected final ExpressionNode leftOperand;
    protected final ExpressionNode rightOperand;

    public BinaryOperator(ExpressionNode leftOperand, ExpressionNode rightOperand) {
        this.leftOperand = leftOperand;
        this.rightOperand = rightOperand;
    }

    @Override
    protected Type doResolveType(ExpressionVisitorContext ctx) {
        Type leftType = leftOperand.resolveType(ctx);
        Type rightType = rightOperand.resolveType(ctx);
        return resolveOperationType(leftType, rightType);
    }

    protected abstract Type resolveOperationType(Type leftOperandType,
                                                 Type rightOperandType);
}

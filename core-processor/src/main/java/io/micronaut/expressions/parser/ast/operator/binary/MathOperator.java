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
import org.objectweb.asm.commons.GeneratorAdapter;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.computeNumericOperationTargetType;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.pushPrimitiveCastIfNecessary;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.pushUnboxPrimitiveIfNecessary;

/**
 * Abstract expression AST node for binary math operations
 * on primitive types.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public abstract sealed class MathOperator extends BinaryOperator permits DivOperator,
                                                                         ModOperator,
                                                                         MulOperator,
                                                                         SubOperator {
    public MathOperator(ExpressionNode leftOperand, ExpressionNode rightOperand) {
        super(leftOperand, rightOperand);
    }

    @Override
    public void generateBytecode(ExpressionVisitorContext ctx) {
        GeneratorAdapter mv = ctx.methodVisitor();
        Type targetType = resolveType(ctx);

        Type leftType = leftOperand.resolveType(ctx);
        leftOperand.compile(ctx);
        pushUnboxPrimitiveIfNecessary(leftType, mv);
        pushPrimitiveCastIfNecessary(leftType, targetType, mv);

        Type rightType = rightOperand.resolveType(ctx);
        rightOperand.compile(ctx);
        pushUnboxPrimitiveIfNecessary(rightType, mv);
        pushPrimitiveCastIfNecessary(rightType, targetType, mv);

        mv.visitInsn(getMathOperationOpcode(ctx));
    }

    @Override
    protected Type resolveOperationType(Type leftOperandType, Type rightOperandType) {
        return computeNumericOperationTargetType(leftOperandType, rightOperandType);
    }

    protected abstract int getMathOperationOpcode(ExpressionVisitorContext ctx);
}

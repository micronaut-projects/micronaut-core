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
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.DOUBLE;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.FLOAT;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.LONG;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isNumeric;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isOneOf;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.toUnboxedIfNecessary;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.pushPrimitiveCastIfNecessary;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.pushUnboxPrimitiveIfNecessary;

/**
 * Expression AST node for '^' operator. '^' operator in evaluated
 * expressions means power operation
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class PowOperator extends BinaryOperator {

    private static final Type MATH_TYPE = Type.getType(Math.class);
    private static final Method POW_METHOD = new Method("pow", DOUBLE, new Type[]{DOUBLE, DOUBLE});

    public PowOperator(ExpressionNode leftOperand, ExpressionNode rightOperand) {
        super(leftOperand, rightOperand);
    }

    @Override
    public void generateBytecode(ExpressionCompilationContext ctx) {
        GeneratorAdapter mv = ctx.methodVisitor();

        Type leftType = leftOperand.resolveType(ctx);
        Type rightType = rightOperand.resolveType(ctx);

        leftOperand.compile(ctx);
        pushUnboxPrimitiveIfNecessary(leftType, mv);
        pushPrimitiveCastIfNecessary(leftType, DOUBLE, mv);

        rightOperand.compile(ctx);
        pushUnboxPrimitiveIfNecessary(leftType, mv);
        pushPrimitiveCastIfNecessary(rightType, DOUBLE, mv);

        mv.invokeStatic(MATH_TYPE, POW_METHOD);

        if (resolveType(ctx) == LONG) {
            pushPrimitiveCastIfNecessary(DOUBLE, LONG, mv);
        }
    }

    @Override
    protected Type resolveOperationType(Type leftOperandType, Type rightOperandType) {
        if (!isNumeric(leftOperandType) || !isNumeric(rightOperandType)) {
            throw new ExpressionCompilationException("Power operation can only be applied to numeric types");
        }

        if (isOneOf(toUnboxedIfNecessary(leftOperandType), DOUBLE, FLOAT) ||
                isOneOf(toUnboxedIfNecessary(rightOperandType), DOUBLE, FLOAT)) {
            return DOUBLE;
        }

        // Int power operation result might not fit in int value
        return LONG;
    }
}

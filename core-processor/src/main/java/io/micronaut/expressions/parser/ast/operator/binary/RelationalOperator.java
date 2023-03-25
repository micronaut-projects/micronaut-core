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
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.BOOLEAN;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.DOUBLE;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.FLOAT;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.LONG;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.computeNumericOperationTargetType;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isNumeric;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isOneOf;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.pushPrimitiveCastIfNecessary;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.pushUnboxPrimitiveIfNecessary;
import static org.objectweb.asm.Opcodes.DCMPL;
import static org.objectweb.asm.Opcodes.FCMPL;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.LCMP;
import static org.objectweb.asm.Type.BOOLEAN_TYPE;

/**
 * Abstract expression AST node for relational operators.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public abstract sealed class RelationalOperator extends BinaryOperator permits GtOperator,
                                                                               GteOperator,
                                                                               LtOperator,
                                                                               LteOperator {
    public RelationalOperator(ExpressionNode leftOperand, ExpressionNode rightOperand) {
        super(leftOperand, rightOperand);
        this.nodeType = BOOLEAN;
    }

    @Override
    protected Type resolveOperationType(Type leftOperandType,
                                        Type rightOperandType) {
        if (!isNumeric(leftOperandType) || !isNumeric(rightOperandType)) {
            throw new ExpressionCompilationException("Relational operation can only be applied to" +
                                                         " numeric types");
        }

        return BOOLEAN_TYPE;
    }

    @Override
    public void generateBytecode(ExpressionVisitorContext ctx) {
        GeneratorAdapter mv = ctx.methodVisitor();

        Type leftType = leftOperand.resolveType(ctx);
        Type rightType = rightOperand.resolveType(ctx);

        Type targetType = computeNumericOperationTargetType(leftType, rightType);

        leftOperand.compile(ctx);
        pushUnboxPrimitiveIfNecessary(leftType, mv);
        pushPrimitiveCastIfNecessary(leftType, targetType, mv);

        rightOperand.compile(ctx);
        pushUnboxPrimitiveIfNecessary(rightType, mv);
        pushPrimitiveCastIfNecessary(rightType, targetType, mv);

        Label elseLabel = new Label();
        Label endOfCmpLabel = new Label();

        if (isOneOf(targetType, DOUBLE, FLOAT, LONG)) {
            String targetDescriptor = targetType.getDescriptor();
            switch (targetDescriptor) {
                case "D" -> mv.visitInsn(DCMPL);
                case "F" -> mv.visitInsn(FCMPL);
                case "J" -> mv.visitInsn(LCMP);
                default -> { }
            }
            mv.visitJumpInsn(nonIntComparisonOpcode(), elseLabel);
        } else {
            mv.visitJumpInsn(intComparisonOpcode(), elseLabel);
        }

        mv.push(true);
        mv.visitJumpInsn(GOTO, endOfCmpLabel);
        mv.visitLabel(elseLabel);
        mv.push(false);
        mv.visitLabel(endOfCmpLabel);
    }

    protected abstract Integer intComparisonOpcode();

    protected abstract Integer nonIntComparisonOpcode();
}

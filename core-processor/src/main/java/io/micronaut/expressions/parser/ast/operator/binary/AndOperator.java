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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import org.objectweb.asm.Label;
import org.objectweb.asm.commons.GeneratorAdapter;

import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IFEQ;

/**
 * Expression AST node for binary {@code &&} operator.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class AndOperator extends LogicalOperator {

    public AndOperator(ExpressionNode leftOperand, ExpressionNode rightOperand) {
        super(leftOperand, rightOperand);
    }

    @Override
    public void generateBytecode(@NonNull ExpressionCompilationContext ctx) {
        GeneratorAdapter mv = ctx.methodVisitor();
        var falseLabel = new Label();
        var trueLabel = new Label();

        pushOperand(ctx, leftOperand, falseLabel);
        pushOperand(ctx, rightOperand, falseLabel);

        mv.push(true);
        mv.visitJumpInsn(GOTO, trueLabel);

        mv.visitLabel(falseLabel);
        mv.push(false);

        mv.visitLabel(trueLabel);
    }

    private void pushOperand(ExpressionCompilationContext ctx, ExpressionNode operand, Label falseLabel) {
        if (operand instanceof AndOperator andOperator) {
            pushOperand(ctx, andOperator.leftOperand, falseLabel);
            pushOperand(ctx, andOperator.rightOperand, falseLabel);
        } else if (operand != null) {
            GeneratorAdapter mv = ctx.methodVisitor();
            operand.compile(ctx);
            mv.visitJumpInsn(IFEQ, falseLabel);
        }
    }
}

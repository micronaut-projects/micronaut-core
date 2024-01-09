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
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Type.BOOLEAN_TYPE;

/**
 * Expression AST node for binary '!=' operator.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class NeqOperator extends EqOperator {
    public NeqOperator(ExpressionNode leftOperand, ExpressionNode rightOperand) {
        super(leftOperand, rightOperand);
    }

    @Override
    public void generateBytecode(ExpressionCompilationContext ctx) {
        super.generateBytecode(ctx);

        GeneratorAdapter mv = ctx.methodVisitor();
        Label elseLabel = new Label();
        Label endOfCmpLabel = new Label();

        mv.visitJumpInsn(IFNE, elseLabel);

        mv.push(true);
        mv.visitJumpInsn(GOTO, endOfCmpLabel);
        mv.visitLabel(elseLabel);
        mv.push(false);
        mv.visitLabel(endOfCmpLabel);
    }

    @Override
    protected Type resolveOperationType(Type leftOperandType, Type rightOperandType) {
        return BOOLEAN_TYPE;
    }
}

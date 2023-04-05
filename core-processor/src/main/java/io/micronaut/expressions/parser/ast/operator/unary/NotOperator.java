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
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isBoolean;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IFNE;

/**
 * Expression node for unary '!' operator.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class NotOperator extends UnaryOperator {
    public NotOperator(ExpressionNode operand) {
        super(operand);
    }

    @Override
    public Type doResolveType(ExpressionVisitorContext ctx) {
        if (nodeType != null) {
            return nodeType;
        }

        Type nodeType = super.doResolveType(ctx);
        if (!isBoolean(nodeType)) {
            throw new ExpressionCompilationException(
                "Invalid unary '!' operation. Unary '!' can only be applied to boolean types");
        }

        this.nodeType = nodeType;
        return nodeType;
    }

    @Override
    public void generateBytecode(ExpressionVisitorContext ctx) {
        GeneratorAdapter mv = ctx.methodVisitor();
        Label falseLabel = new Label();
        Label returnLabel = new Label();

        operand.compile(ctx);
        mv.visitJumpInsn(IFNE, falseLabel);
        mv.push(true);
        mv.visitJumpInsn(GOTO, returnLabel);

        mv.visitLabel(falseLabel);
        mv.push(false);

        mv.visitLabel(returnLabel);
    }
}

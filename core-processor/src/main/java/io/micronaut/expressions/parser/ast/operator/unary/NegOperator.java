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
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.DOUBLE;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.DOUBLE_WRAPPER;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.FLOAT;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.FLOAT_WRAPPER;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.INT;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.INT_WRAPPER;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.LONG;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.LONG_WRAPPER;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isNumeric;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isOneOf;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.pushUnboxPrimitiveIfNecessary;
import static org.objectweb.asm.Opcodes.DNEG;
import static org.objectweb.asm.Opcodes.FNEG;
import static org.objectweb.asm.Opcodes.INEG;
import static org.objectweb.asm.Opcodes.LNEG;

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
    public Type doResolveType(ExpressionVisitorContext ctx) {
        Type nodeType = super.doResolveType(ctx);
        if (!isNumeric(nodeType)) {
            throw new ExpressionCompilationException(
                "Invalid unary '-' operation. Unary '-' can only be applied to numeric types");
        }
        return nodeType;
    }

    @Override
    public void generateBytecode(ExpressionVisitorContext ctx) {
        GeneratorAdapter mv = ctx.methodVisitor();

        operand.compile(ctx);
        pushUnboxPrimitiveIfNecessary(operand.resolveType(ctx), mv);

        if (isOneOf(operand.resolveType(ctx), INT, INT_WRAPPER)) {
            mv.visitInsn(INEG);
        } else if (isOneOf(operand.resolveType(ctx), DOUBLE, DOUBLE_WRAPPER)) {
            mv.visitInsn(DNEG);
        } else if (isOneOf(operand.resolveType(ctx), FLOAT, FLOAT_WRAPPER)) {
            mv.visitInsn(FNEG);
        } else if (isOneOf(operand.resolveType(ctx), LONG, LONG_WRAPPER)) {
            mv.visitInsn(LNEG);
        } else {
            throw new ExpressionCompilationException(
                "Invalid unary '-' operation. Unary '-' can only be applied to numeric types");
        }
    }
}

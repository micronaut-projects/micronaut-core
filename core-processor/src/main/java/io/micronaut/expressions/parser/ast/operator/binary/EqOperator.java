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
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.Objects;

import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.pushBoxPrimitiveIfNecessary;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.BOOLEAN;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.OBJECT;

/**
 * Expression AST node for binary '==' operator.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public sealed class EqOperator extends BinaryOperator permits NeqOperator {

    public EqOperator(ExpressionNode leftOperand, ExpressionNode rightOperand) {
        super(leftOperand, rightOperand);
    }

    @Override
    public void generateBytecode(@NonNull ExpressionCompilationContext ctx) {
        GeneratorAdapter mv = ctx.methodVisitor();
        Type lefType = leftOperand.resolveType(ctx);
        Type rightType = rightOperand.resolveType(ctx);

        leftOperand.compile(ctx);
        pushBoxPrimitiveIfNecessary(lefType, mv);

        rightOperand.compile(ctx);
        pushBoxPrimitiveIfNecessary(rightType, mv);

        mv.invokeStatic(Type.getType(Objects.class), new Method("equals", BOOLEAN, new Type[] {OBJECT, OBJECT}));
    }

    @Override
    protected Type resolveOperationType(Type leftOperandType, Type rightOperandType) {
        return BOOLEAN;
    }
}

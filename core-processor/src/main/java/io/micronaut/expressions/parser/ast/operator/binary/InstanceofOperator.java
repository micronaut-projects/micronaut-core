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
import io.micronaut.expressions.parser.ast.types.TypeIdentifier;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.BOOLEAN;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isPrimitive;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.pushBoxPrimitiveIfNecessary;
import static org.objectweb.asm.Opcodes.INSTANCEOF;

/**
 * Expression AST node for 'instanceof' operator.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class InstanceofOperator extends ExpressionNode {
    private final ExpressionNode operand;
    private final TypeIdentifier typeIdentifier;

    public InstanceofOperator(ExpressionNode operand, TypeIdentifier typeIdentifier) {
        this.operand = operand;
        this.typeIdentifier = typeIdentifier;
    }

    @Override
    public void generateBytecode(ExpressionCompilationContext ctx) {
        Type targetType = typeIdentifier.resolveType(ctx);
        if (isPrimitive(targetType)) {
            throw new ExpressionCompilationException(
                "'instanceof' operation can not be used with primitive right-hand side type");
        }

        GeneratorAdapter mv = ctx.methodVisitor();
        Type expressionType = operand.resolveType(ctx);

        operand.compile(ctx);
        pushBoxPrimitiveIfNecessary(expressionType, mv);

        mv.visitTypeInsn(INSTANCEOF, targetType.getInternalName());
    }

    @Override
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        return PrimitiveElement.BOOLEAN;
    }

    @Override
    protected Type doResolveType(@NonNull ExpressionVisitorContext ctx) {
        return BOOLEAN;
    }
}

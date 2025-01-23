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
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isPrimitive;

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
    public ExpressionDef generateExpression(ExpressionCompilationContext ctx) {
        TypeDef targetType = typeIdentifier.resolveType(ctx);
        if (isPrimitive(targetType)) {
            throw new ExpressionCompilationException(
                "'instanceof' operation can not be used with primitive right-hand side type");
        }
        return operand.compile(ctx).instanceOf((ClassTypeDef) targetType);
    }

    @Override
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        return PrimitiveElement.BOOLEAN;
    }

    @Override
    protected TypeDef doResolveType(@NonNull ExpressionVisitorContext ctx) {
        return TypeDef.Primitive.BOOLEAN;
    }
}

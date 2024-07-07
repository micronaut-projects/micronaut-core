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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import org.objectweb.asm.Type;

/**
 * Abstract expression node for unary operators.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public abstract sealed class UnaryOperator extends ExpressionNode permits EmptyOperator, NegOperator, NotOperator, PosOperator {

    protected final ExpressionNode operand;

    public UnaryOperator(ExpressionNode operand) {
        this.operand = operand;
    }

    @NonNull
    @Override
    public Type doResolveType(@NonNull ExpressionVisitorContext ctx) {
        return operand.resolveType(ctx);
    }

    @Override
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        Type type = doResolveType(ctx);
        try {
            return PrimitiveElement.valueOf(type.getClassName());
        } catch (IllegalArgumentException e) {
            return ClassElement.of(type.getClassName());
        }
    }
}

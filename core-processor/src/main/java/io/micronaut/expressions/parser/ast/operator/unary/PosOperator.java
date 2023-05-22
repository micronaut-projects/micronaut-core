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

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isNumeric;

/**
 * Expression node for unary '+' operator.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class PosOperator extends UnaryOperator {
    public PosOperator(ExpressionNode operand) {
        super(operand);
    }

    @Override
    public Type doResolveType(ExpressionVisitorContext ctx) {
        Type nodeType = super.doResolveType(ctx);

        if (!isNumeric(nodeType)) {
            throw new ExpressionCompilationException("Invalid unary '+' operation");
        }

        return nodeType;
    }

    @Override
    public void generateBytecode(ExpressionVisitorContext ctx) {
        operand.compile(ctx);
    }
}

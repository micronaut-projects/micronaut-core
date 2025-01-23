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
import io.micronaut.sourcegen.model.ExpressionDef;

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
    public ExpressionDef generateExpression(ExpressionCompilationContext ctx) {
        return leftOperand.compile(ctx).isTrue().and(rightOperand.compile(ctx).isTrue());
    }

}

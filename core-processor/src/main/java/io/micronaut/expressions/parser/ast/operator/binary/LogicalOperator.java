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
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import org.objectweb.asm.Type;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isBoolean;
import static org.objectweb.asm.Type.BOOLEAN_TYPE;

/**
 * Abstract expression AST node for binary logical operator.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public abstract sealed class LogicalOperator extends BinaryOperator permits AndOperator, OrOperator {

    public LogicalOperator(ExpressionNode leftOperand, ExpressionNode rightOperand) {
        super(leftOperand, rightOperand);
    }

    @Override
    protected Type resolveOperationType(Type leftOperandType,
                                        Type rightOperandType) {
        if (!isBoolean(leftOperandType) && !isBoolean(rightOperandType)) {
            throw new ExpressionCompilationException("Logical operation can only be applied to boolean types");
        }

        return BOOLEAN_TYPE;
    }
}

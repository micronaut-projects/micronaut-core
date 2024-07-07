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
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import org.objectweb.asm.Type;

import java.util.Map;
import java.util.Optional;

import static org.objectweb.asm.Opcodes.DREM;
import static org.objectweb.asm.Opcodes.FREM;
import static org.objectweb.asm.Opcodes.IREM;
import static org.objectweb.asm.Opcodes.LREM;

/**
 * Expression AST node for binary '/' operator.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class ModOperator extends MathOperator {

    private static final Map<String, Integer> MOD_OPERATION_OPCODES = Map.of(
        "D", DREM,
        "I", IREM,
        "F", FREM,
        "J", LREM
    );

    public ModOperator(ExpressionNode leftOperand, ExpressionNode rightOperand) {
        super(leftOperand, rightOperand);
    }

    @Override
    protected int getMathOperationOpcode(ExpressionCompilationContext ctx) {
        Type type = resolveType(ctx);
        String typeDescriptor = type.getDescriptor();
        return Optional.ofNullable(MOD_OPERATION_OPCODES.get(typeDescriptor))
            .orElseThrow(() -> new ExpressionCompilationException("'%' operation can not be applied to " + type));
    }
}

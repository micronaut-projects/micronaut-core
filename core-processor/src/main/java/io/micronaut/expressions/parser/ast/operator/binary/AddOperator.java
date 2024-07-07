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
import io.micronaut.expressions.parser.ast.util.TypeDescriptors;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.Map;
import java.util.Optional;

import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.pushPrimitiveCastIfNecessary;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.pushUnboxPrimitiveIfNecessary;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.STRING;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.VOID;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.computeNumericOperationTargetType;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isNumeric;
import static org.objectweb.asm.Opcodes.DADD;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.FADD;
import static org.objectweb.asm.Opcodes.IADD;
import static org.objectweb.asm.Opcodes.LADD;
import static org.objectweb.asm.Opcodes.NEW;

/**
 * Expression node for binary '+' operator. Works both for math operation and string
 * concatenation.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class AddOperator extends BinaryOperator {

    private static final Map<String, Integer> ADD_OPERATION_OPCODES = Map.of(
        "D", DADD,
        "I", IADD,
        "F", FADD,
        "J", LADD
    );

    private static final Type STRING_BUILDER_TYPE = Type.getType(StringBuilder.class);

    private static final Method STRING_BUILD_CONSTRUCTOR =
        new Method("<init>", VOID, new Type[] {});

    private static final Method STRING_BUILD_TO_STRING =
        new Method("toString", STRING, new Type[] {});

    public AddOperator(ExpressionNode leftOperand, ExpressionNode rightOperand) {
        super(leftOperand, rightOperand);
    }

    @Override
    protected Type resolveOperationType(Type leftOperandType, Type rightOperandType) {
        if (!(leftOperandType.equals(STRING)
            || rightOperandType.equals(STRING)
            || (isNumeric(leftOperandType) && isNumeric(rightOperandType)))) {
            throw new ExpressionCompilationException("'+' operation can only be applied to numeric and string types");
        }

        if (leftOperandType.equals(STRING)
            || rightOperandType.equals(STRING)) {
            return STRING;
        }

        return computeNumericOperationTargetType(leftOperandType, rightOperandType);
    }

    @Override
    public void generateBytecode(@NonNull ExpressionCompilationContext ctx) {
        Type leftType = leftOperand.resolveType(ctx);
        Type rightType = rightOperand.resolveType(ctx);

        GeneratorAdapter mv = ctx.methodVisitor();
        if (leftType.equals(STRING) || (rightType.equals(STRING))) {
            concatStrings(ctx);
        } else {
            Type targetType = resolveType(ctx);

            leftOperand.compile(ctx);
            pushUnboxPrimitiveIfNecessary(leftType, mv);
            pushPrimitiveCastIfNecessary(leftType, targetType, mv);

            rightOperand.compile(ctx);
            pushUnboxPrimitiveIfNecessary(rightType, mv);
            pushPrimitiveCastIfNecessary(rightType, targetType, mv);

            int opcode = Optional.ofNullable(ADD_OPERATION_OPCODES.get(targetType.getDescriptor()))
                .orElseThrow(() -> new ExpressionCompilationException("Can not apply '+' operation to " + targetType));

            mv.visitInsn(opcode);
        }
    }

    private void concatStrings(ExpressionCompilationContext ctx) {
        GeneratorAdapter mv = ctx.methodVisitor();
        initStringBuilder(mv);
        pushOperand(ctx, leftOperand);
        pushOperand(ctx, rightOperand);
        mv.invokeVirtual(STRING_BUILDER_TYPE, STRING_BUILD_TO_STRING);
    }

    private void initStringBuilder(GeneratorAdapter mv) {
        mv.visitTypeInsn(NEW, STRING_BUILDER_TYPE.getInternalName());
        mv.visitInsn(DUP);
        mv.invokeConstructor(STRING_BUILDER_TYPE, STRING_BUILD_CONSTRUCTOR);
    }

    private void pushOperand(ExpressionCompilationContext ctx, ExpressionNode operand) {
        GeneratorAdapter mv = ctx.methodVisitor();
        if (operand instanceof AddOperator addOperator) {
            Type operatorType = addOperator.resolveType(ctx);
            if (operatorType.equals(STRING)) {
                pushOperand(ctx, addOperator.leftOperand);
                pushOperand(ctx, addOperator.rightOperand);
            } else {
                addOperator.compile(ctx);
                pushAppendMethod(operand.resolveType(ctx), mv);
            }
        } else if (operand != null) {
            operand.compile(ctx);
            pushAppendMethod(operand.resolveType(ctx), mv);
        }
    }

    private void pushAppendMethod(Type operandType, GeneratorAdapter mv) {
        Type argumentType = TypeDescriptors.isPrimitive(operandType)
            ? operandType
            : Type.getType(Object.class);

        var appendMethod = new Method("append", STRING_BUILDER_TYPE, new Type[] {argumentType});
        mv.invokeVirtual(STRING_BUILDER_TYPE, appendMethod);
    }
}

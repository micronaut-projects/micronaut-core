/*
 * Copyright 2017-2023 original authors
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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.ast.util.TypeDescriptors;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.processing.JavaModelUtils;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.Optional;

import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.pushBoxPrimitiveIfNecessary;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.toBoxedIfNecessary;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IFGE;
import static org.objectweb.asm.Opcodes.IFGT;
import static org.objectweb.asm.Opcodes.IFLE;
import static org.objectweb.asm.Opcodes.IFLT;
import static org.objectweb.asm.Type.BOOLEAN_TYPE;

/**
 * Expression AST node for relational operations ({@literal >}, {@literal <}, {@code >=}, {@code <=}) on
 * types that implement {@link Comparable} interface.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class ComparablesComparisonOperation extends ExpressionNode {

    private static final String COMPARABLE_CLASS_NAME = Comparable.class.getName();

    private final ExpressionNode leftOperand;
    private final ExpressionNode rightOperand;
    private final int comparisonOpcode;

    private ClassElement comparableTypeArgument;
    private ComparisonType comparisonType;

    public ComparablesComparisonOperation(ExpressionNode leftOperand,
                                          ExpressionNode rightOperand,
                                          int comparisonOpcode) {
        this.leftOperand = leftOperand;
        this.rightOperand = rightOperand;
        this.comparisonOpcode = comparisonOpcode;
    }

    @NonNull
    @Override
    protected Type doResolveType(@NonNull ExpressionVisitorContext ctx) {
        // resolving non-primitive class elements is necessary to handle cases
        // when one of expression nodes is of primitive type, but other expression node
        // is comparable to respective boxed type
        ClassElement leftClassElement = resolveNonPrimitiveClassElement(leftOperand, ctx);
        ClassElement rightClassElement = resolveNonPrimitiveClassElement(rightOperand, ctx);

        ClassElement leftComparableTypeArgument = resolveComparableTypeArgument(leftClassElement);
        ClassElement rightComparableTypeArgument = resolveComparableTypeArgument(rightClassElement);

        if (leftComparableTypeArgument != null && rightClassElement.isAssignable(leftComparableTypeArgument)) {
            comparisonType = ComparisonType.LEFT;
            comparableTypeArgument = leftComparableTypeArgument;
        } else if (rightComparableTypeArgument != null && leftClassElement.isAssignable(rightComparableTypeArgument)) {
            comparisonType = ComparisonType.RIGHT;
            comparableTypeArgument = rightComparableTypeArgument;
        } else {
            throw new ExpressionCompilationException(
                "Comparison operation can only be applied to numeric types or types that are Comparable to each other");
        }

        return BOOLEAN_TYPE;
    }

    /**
     * Resolves {@link ClassElement} of passed {@link ExpressionNode}, returning original
     * {@link ClassElement} of node when it is of object type or boxed type in case
     * {@link ExpressionNode} resolves to primitive type.
     */
    private ClassElement resolveNonPrimitiveClassElement(ExpressionNode expressionNode,
                                                         ExpressionVisitorContext ctx) {
        ClassElement classElement = expressionNode.resolveClassElement(ctx);
        if (classElement instanceof PrimitiveElement) {
            return ctx.visitorContext()
                .getClassElement(toBoxedIfNecessary(expressionNode.resolveType(ctx)).getClassName())
                .orElseThrow();
        }
        return classElement;
    }

    @Nullable
    private ClassElement resolveComparableTypeArgument(ClassElement classElement) {
        return Optional.ofNullable(classElement
                .getAllTypeArguments()
                .get(COMPARABLE_CLASS_NAME))
            .map(types -> types.get("T"))
            .orElse(null);
    }

    @Override
    public void generateBytecode(@NonNull ExpressionCompilationContext ctx) {
        GeneratorAdapter mv = ctx.methodVisitor();

        var elseLabel = new Label();
        var endOfCmpLabel = new Label();

        if (comparisonType == ComparisonType.LEFT) {
            pushCompareToMethodCall(leftOperand, rightOperand, ctx);
            mv.visitJumpInsn(comparisonOpcode, elseLabel);
        } else {
            pushCompareToMethodCall(rightOperand, leftOperand, ctx);
            mv.visitJumpInsn(invertInstruction(comparisonOpcode), elseLabel);
        }

        mv.push(true);
        mv.visitJumpInsn(GOTO, endOfCmpLabel);
        mv.visitLabel(elseLabel);
        mv.push(false);
        mv.visitLabel(endOfCmpLabel);
    }

    private void pushCompareToMethodCall(ExpressionNode comparableNode,
                                         ExpressionNode comparedNode,
                                         ExpressionCompilationContext ctx) {
        GeneratorAdapter mv = ctx.methodVisitor();
        ClassElement comparableClass = comparableNode.resolveClassElement(ctx);

        Type comparableType = comparableNode.resolveType(ctx);
        Type comparedType = comparedNode.resolveType(ctx);

        comparableNode.compile(ctx);
        pushBoxPrimitiveIfNecessary(comparableType, mv);

        comparedNode.compile(ctx);
        pushBoxPrimitiveIfNecessary(comparedType, mv);

        if (comparableClass.isInterface()) {
            mv.invokeInterface(comparableType,
                new Method("compareTo", TypeDescriptors.INT,
                    new org.objectweb.asm.Type[] {TypeDescriptors.OBJECT}));
        } else {
            mv.invokeVirtual(comparableType,
                new Method("compareTo", TypeDescriptors.INT,
                    new org.objectweb.asm.Type[] {JavaModelUtils.getTypeReference(comparableTypeArgument)}));
        }
    }

    private Integer invertInstruction(Integer instruction) {
        return switch (instruction) {
            case IFLE -> IFGE;
            case IFLT -> IFGT;
            case IFGE -> IFLE;
            case IFGT -> IFLT;
            default -> instruction;
        };
    }

    private enum ComparisonType {

        /**
         * Comparison type for cases when left compared value implements {@link Comparable}
         * interface and right element of comparison expression is assignable to generic
         * type parameter of left value.
         */
        LEFT,

        /**
         * Comparison type for cases when right compared value implements {@link Comparable}
         * interface and left element of comparison expression is assignable to generic
         * type parameter of right value.
         */
        RIGHT
    }
}

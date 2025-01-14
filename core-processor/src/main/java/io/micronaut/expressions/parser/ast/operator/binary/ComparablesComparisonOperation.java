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
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.lang.reflect.Method;
import java.util.Optional;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.BOOLEAN;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.toBoxedIfNecessary;

/**
 * Expression AST node for relational operations ({@literal >}, {@literal <}, {@code >=}, {@code <=}) on
 * types that implement {@link Comparable} interface.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class ComparablesComparisonOperation extends ExpressionNode {

    private static final ClassTypeDef COMPARABLE_TYPE = ClassTypeDef.of(Comparable.class);

    private static final Method COMPARE_METHOD =
        ReflectionUtils.getRequiredMethod(Comparable.class, "compareTo", Object.class);

    private final ExpressionNode leftOperand;
    private final ExpressionNode rightOperand;
    private final ExpressionDef.ComparisonOperation.OpType type;
    private ComparisonType comparisonType;

    public ComparablesComparisonOperation(ExpressionNode leftOperand,
                                          ExpressionNode rightOperand,
                                          ExpressionDef.ComparisonOperation.OpType type) {
        this.leftOperand = leftOperand;
        this.rightOperand = rightOperand;
        this.type = type;
    }

    @Override
    protected TypeDef doResolveType(@NonNull ExpressionVisitorContext ctx) {
        // resolving non-primitive class elements is necessary to handle cases
        // when one of expression nodes is of primitive type, but other expression node
        // is comparable to respective boxed type
        ClassElement leftClassElement = resolveNonPrimitiveClassElement(leftOperand, ctx);
        ClassElement rightClassElement = resolveNonPrimitiveClassElement(rightOperand, ctx);

        ClassElement leftComparableTypeArgument = resolveComparableTypeArgument(leftClassElement);
        ClassElement rightComparableTypeArgument = resolveComparableTypeArgument(rightClassElement);

        if (leftComparableTypeArgument != null && rightClassElement.isAssignable(leftComparableTypeArgument)) {
            comparisonType = ComparisonType.LEFT;
        } else if (rightComparableTypeArgument != null && leftClassElement.isAssignable(rightComparableTypeArgument)) {
            comparisonType = ComparisonType.RIGHT;
        } else {
            throw new ExpressionCompilationException(
                "Comparison operation can only be applied to numeric types or types that are " +
                    "Comparable to each other");
        }
        return BOOLEAN;
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
                .getClassElement(toBoxedIfNecessary(expressionNode.resolveType(ctx)).getName())
                .orElseThrow();
        }
        return classElement;
    }

    @Nullable
    private ClassElement resolveComparableTypeArgument(ClassElement classElement) {
        return Optional.ofNullable(classElement
                .getAllTypeArguments()
                .get(COMPARABLE_TYPE.getName()))
            .map(types -> types.get("T"))
            .orElse(null);
    }

    @Override
    public ExpressionDef generateExpression(ExpressionCompilationContext ctx) {
        if (comparisonType == ComparisonType.LEFT) {
            return compareComparable(leftOperand, rightOperand, ctx)
                .compare(type, ExpressionDef.constant(0));
        } else {
            return compareComparable(rightOperand, leftOperand, ctx)
                .compare(invert(type), ExpressionDef.constant(0));
        }
    }

    private ExpressionDef compareComparable(ExpressionNode comparableNode,
                                            ExpressionNode comparedNode,
                                            ExpressionCompilationContext ctx) {
        return comparableNode.compile(ctx)
            .cast(COMPARABLE_TYPE).invoke(
                COMPARE_METHOD,

                comparedNode.compile(ctx)
            );
    }

    private ExpressionDef.ComparisonOperation.OpType invert(ExpressionDef.ComparisonOperation.OpType instruction) {
        // INVESTIGATE: LESS_THEN should be inverted to GREATER_THAN_OR_EQUAL and the opposites
        return switch (instruction) {
            case EQUAL_TO -> ExpressionDef.ComparisonOperation.OpType.NOT_EQUAL_TO;
            case NOT_EQUAL_TO -> ExpressionDef.ComparisonOperation.OpType.EQUAL_TO;
            case GREATER_THAN -> ExpressionDef.ComparisonOperation.OpType.LESS_THAN;
            case LESS_THAN -> ExpressionDef.ComparisonOperation.OpType.GREATER_THAN;
            case GREATER_THAN_OR_EQUAL -> ExpressionDef.ComparisonOperation.OpType.LESS_THAN_OR_EQUAL;
            case LESS_THAN_OR_EQUAL -> ExpressionDef.ComparisonOperation.OpType.GREATER_THAN_OR_EQUAL;
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

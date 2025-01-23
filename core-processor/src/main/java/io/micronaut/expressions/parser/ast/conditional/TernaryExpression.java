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
package io.micronaut.expressions.parser.ast.conditional;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;

import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.getRequiredClassElement;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.isAssignable;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.BOOLEAN;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.BOOLEAN_WRAPPER;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.OBJECT;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.computeNumericOperationTargetType;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isNumeric;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isOneOf;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.toUnboxedIfNecessary;

/**
 * Expression AST node for ternary expressions.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public class TernaryExpression extends ExpressionNode {
    private static final java.lang.reflect.Method COERCE_TO_BOOLEAN =
        ReflectionUtils.getRequiredMethod(ObjectUtils.class, "coerceToBoolean", Object.class);

    private final ExpressionNode condition;
    private final ExpressionNode trueExpr;
    private final ExpressionNode falseExpr;

    public TernaryExpression(ExpressionNode condition, ExpressionNode trueExpr,
                             ExpressionNode falseExpr) {
        this.condition = condition;
        this.trueExpr = trueExpr;
        this.falseExpr = falseExpr;
    }

    @Override
    public ExpressionDef generateExpression(ExpressionCompilationContext ctx) {
        TypeDef numericType = null;
        TypeDef trueType = trueExpr.resolveType(ctx);
        TypeDef falseType = falseExpr.resolveType(ctx);
        if (isNumeric(trueType) && isNumeric(falseType)) {
            numericType = computeNumericOperationTargetType(
                toUnboxedIfNecessary(trueType),
                toUnboxedIfNecessary(falseType));
        }

        ExpressionDef exp;
        if (shouldCoerceConditionToBoolean()) {
            exp = ClassTypeDef.of(ObjectUtils.class).invokeStatic(COERCE_TO_BOOLEAN, condition.compile(ctx));
        } else {
            exp = condition.compile(ctx);
        }
        return exp.isTrue().doIfElse(
                numericType == null ? trueExpr.compile(ctx) : trueExpr.compile(ctx).cast(numericType),
                numericType == null ? falseExpr.compile(ctx) : falseExpr.compile(ctx).cast(numericType)
            );
    }

    /**
     * @return Whether the condition should be coerced to a boolean type.
     */
    protected boolean shouldCoerceConditionToBoolean() {
        return false;
    }

    @Override
    protected TypeDef doResolveType(@NonNull ExpressionVisitorContext ctx) {
        if (!shouldCoerceConditionToBoolean() && !isOneOf(condition.resolveType(ctx), BOOLEAN, BOOLEAN_WRAPPER)) {
            throw new ExpressionCompilationException("Invalid ternary operator. Condition should resolve to boolean type");
        }

        TypeDef trueType = trueExpr.resolveType(ctx);
        TypeDef falseType = falseExpr.resolveType(ctx);

        if (trueType.equals(falseType)) {
            return trueType;
        }

        if (isNumeric(trueType) && isNumeric(falseType)) {
            return computeNumericOperationTargetType(
                toUnboxedIfNecessary(trueType),
                toUnboxedIfNecessary(falseType));
        } else if (isNumeric(trueType) || isNumeric(falseType)) {
            return OBJECT;
        }

        ClassElement trueClassElement = getRequiredClassElement(trueType, ctx.visitorContext());
        ClassElement falseClassElement = getRequiredClassElement(falseType, ctx.visitorContext());

        if (isAssignable(trueClassElement, falseClassElement)) {
            return trueType;
        }

        if (isAssignable(falseClassElement, trueClassElement)) {
            return falseType;
        }

        return OBJECT;
    }
}

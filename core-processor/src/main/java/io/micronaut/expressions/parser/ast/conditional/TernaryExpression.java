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
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.BOOLEAN;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.BOOLEAN_WRAPPER;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.OBJECT;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.computeNumericOperationTargetType;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isNumeric;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isOneOf;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.toUnboxedIfNecessary;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.getRequiredClassElement;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.isAssignable;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.pushBoxPrimitiveIfNecessary;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.pushPrimitiveCastIfNecessary;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.pushUnboxPrimitiveIfNecessary;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.commons.GeneratorAdapter.NE;

/**
 * Expression AST node for ternary expressions.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public class TernaryExpression extends ExpressionNode {
    private static final Method COERCE_TO_BOOLEAN = Method.getMethod(
        ReflectionUtils.getRequiredMethod(ObjectUtils.class, "coerceToBoolean", Object.class)
    );
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
    public void generateBytecode(ExpressionCompilationContext ctx) {
        GeneratorAdapter mv = ctx.methodVisitor();
        Label falseLabel = new Label();
        Label returnLabel = new Label();

        Type trueType = trueExpr.resolveType(ctx);
        Type falseType = falseExpr.resolveType(ctx);
        Type numericType = null;
        if (isNumeric(trueType) && isNumeric(falseType)) {
            numericType = computeNumericOperationTargetType(
                toUnboxedIfNecessary(trueType),
                toUnboxedIfNecessary(falseType));
        }

        mv.push(true);
        Type conditionType = condition.resolveType(ctx);

        condition.compile(ctx);
        if (shouldCoerceConditionToBoolean()) {
            pushBoxPrimitiveIfNecessary(conditionType, mv);
            mv.invokeStatic(
                Type.getType(ObjectUtils.class),
                COERCE_TO_BOOLEAN
            );
        } else {
            pushUnboxPrimitiveIfNecessary(conditionType, mv);
        }

        mv.ifCmp(BOOLEAN, NE, falseLabel);
        trueExpr.compile(ctx);
        if (numericType != null) {
            pushPrimitiveCastIfNecessary(trueType, numericType, mv);
        } else {
            pushBoxPrimitiveIfNecessary(trueType, mv);
        }

        mv.visitJumpInsn(GOTO, returnLabel);

        mv.visitLabel(falseLabel);
        falseExpr.compile(ctx);
        if (numericType != null) {
            pushPrimitiveCastIfNecessary(falseType, numericType, mv);
        } else {
            pushBoxPrimitiveIfNecessary(falseType, mv);
        }

        mv.visitLabel(returnLabel);
    }

    @Override
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        String className = doResolveType(ctx).getClassName();
        return ctx.visitorContext().getClassElement(className)
            .orElse(ClassElement.of(className));
    }

    /**
     * @return Whether the condition should be coerced to a boolean type.
     */
    protected boolean shouldCoerceConditionToBoolean() {
        return false;
    }

    @Override
    protected Type doResolveType(@NonNull ExpressionVisitorContext ctx) {
        if (!shouldCoerceConditionToBoolean() && !isOneOf(condition.resolveType(ctx), BOOLEAN, BOOLEAN_WRAPPER)) {
            throw new ExpressionCompilationException("Invalid ternary operator. Condition should resolve to boolean type");
        }

        Type trueType = trueExpr.resolveType(ctx);
        Type falseType = falseExpr.resolveType(ctx);

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

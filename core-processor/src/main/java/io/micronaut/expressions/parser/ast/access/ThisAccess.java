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
package io.micronaut.expressions.parser.ast.access;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.expressions.ExpressionEvaluationContext;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.sourcegen.model.ExpressionDef;

import java.lang.reflect.Method;

/**
 * Enables access to 'this' in non-static contexts.
 */
@Internal
public final class ThisAccess extends ExpressionNode {

    private static final Method GET_THIS_METHOD =
        ReflectionUtils.getRequiredMethod(ExpressionEvaluationContext.class, "getThis");

    @Override
    protected ExpressionDef generateExpression(ExpressionCompilationContext ctx) {
        return ctx.expressionEvaluationContextVar()
            .invoke(GET_THIS_METHOD)
            .cast(resolveType(ctx));
    }

    @Override
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        ClassElement thisType = ctx.evaluationContext().findThis();
        if (thisType == null) {
            throw new ExpressionCompilationException(
                "Cannot reference 'this' from the current context.");
        }
        return thisType;
    }

}

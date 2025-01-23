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
package io.micronaut.expressions.parser.ast.access;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.expressions.context.ExpressionEvaluationContext;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Expression node used for invocation of method from expression
 * evaluation context.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class ContextMethodCall extends AbstractMethodCall {

    private static final Method GET_BEAN_METHOD =
        ReflectionUtils.getRequiredMethod(io.micronaut.core.expressions.ExpressionEvaluationContext.class, "getBean", Class.class);

    public ContextMethodCall(String name, List<ExpressionNode> arguments) {
        super(name, arguments);
    }

    @Override
    CandidateMethod resolveUsedMethod(ExpressionVisitorContext ctx) {
        List<TypeDef> argumentTypes = resolveArgumentTypes(ctx);

        ExpressionEvaluationContext evaluationContext = ctx.evaluationContext();
        List<CandidateMethod> candidateMethods =
            evaluationContext.findMethods(name)
                .stream()
                .map(method -> toCandidateMethod(ctx, method, argumentTypes))
                .filter(CandidateMethod::isMatching)
                .toList();

        if (candidateMethods.isEmpty()) {
            throw new ExpressionCompilationException(
                "No method [ " + name + stringifyArguments(ctx) + " ] available in evaluation context");
        } else if (candidateMethods.size() > 1) {
            throw new ExpressionCompilationException(
                "Ambiguous expression evaluation context reference. Found " + candidateMethods.size() +
                    " matching methods: " + candidateMethods);
        }

        return candidateMethods.iterator().next();
    }

    @Override
    public ExpressionDef generateExpression(ExpressionCompilationContext ctx) {
        TypeDef calleeType = usedMethod.getOwningType();

        return ctx.expressionEvaluationContextVar()
            .invoke(GET_BEAN_METHOD, ExpressionDef.constant(calleeType))
            .cast(calleeType)
            .invoke(usedMethod.getMethodElement(), compileArguments(ctx));
    }
}

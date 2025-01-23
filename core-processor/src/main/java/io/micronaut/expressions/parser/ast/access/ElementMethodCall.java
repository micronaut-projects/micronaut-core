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
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.ast.types.TypeIdentifier;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.util.List;
import java.util.Optional;

/**
 * Expression AST node used for method invocation.
 * This node represents both object method invocation and static method
 * invocation
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public sealed class ElementMethodCall extends AbstractMethodCall permits PropertyAccess {

    private static final java.lang.reflect.Method METHOD_OR_ELSE = ReflectionUtils.getRequiredInternalMethod(Optional.class, "orElse", Object.class);
    protected final ExpressionNode callee;
    private final boolean nullSafe;

    public ElementMethodCall(ExpressionNode callee,
                             String name,
                             List<ExpressionNode> arguments,
                             boolean nullSafe) {
        super(name, arguments);
        this.callee = callee;
        this.nullSafe = nullSafe;
    }

    /**
     * @return Is the method call null safe
     */
    protected boolean isNullSafe() {
        return nullSafe;
    }

    @Override
    protected ExpressionDef generateExpression(ExpressionCompilationContext ctx) {
        ClassElement calleeClass = callee.resolveClassElement(ctx);

        if (callee instanceof TypeIdentifier) {
            return ClassTypeDef.of(calleeClass)
                .invokeStatic(usedMethod.getMethodElement(), compileArguments(ctx));
        }
        ExpressionDef exp = callee.compile(ctx);
        if (nullSafe) {
            if (calleeClass.isAssignable(Optional.class)) {
                // safe navigate optional
                // recompute new return type
                calleeClass = calleeClass.getFirstTypeArgument().orElse(ClassElement.of(Object.class));
                exp = exp.invoke(METHOD_OR_ELSE, ExpressionDef.nullValue())
                    .cast(TypeDef.erasure(calleeClass));
            }
            StatementDef.DefineAndAssign local = exp.newLocal(name + "Var");
            ctx.additionalStatements().add(local);
            ctx.additionalStatements().add(
                local.variable().isNull()
                    .doIf(ExpressionDef.nullValue().returning())
            );
            exp = local.variable();
        }
        return exp.invoke(usedMethod.getMethodElement(), compileArguments(ctx));
    }

    @Override
    CandidateMethod resolveUsedMethod(ExpressionVisitorContext ctx) {
        List<TypeDef> argumentTypes = resolveArgumentTypes(ctx);
        ClassElement classElement = callee.resolveClassElement(ctx);

        if (isNullSafe() && classElement.isAssignable(Optional.class)) {
            // safe navigate optional
            classElement = classElement.getFirstTypeArgument().orElse(classElement);
        }

        ElementQuery<MethodElement> methodQuery = buildMethodQuery();
        List<CandidateMethod> candidateMethods = classElement.getEnclosedElements(methodQuery).stream()
                .map(method -> toCandidateMethod(ctx, method, argumentTypes))
                .filter(CandidateMethod::isMatching)
                .toList();

        if (candidateMethods.isEmpty()) {
            throw new ExpressionCompilationException(
                "No method [ " + name + stringifyArguments(ctx) + " ] available in class " + classElement.getName());
        } else if (candidateMethods.size() > 1) {
            throw new ExpressionCompilationException(
                "Ambiguous method call. Found " + candidateMethods.size() +
                    " matching methods: " + candidateMethods + " in class " + classElement.getName());
        }

        return candidateMethods.iterator().next();
    }

    private ElementQuery<MethodElement> buildMethodQuery() {
        ElementQuery<MethodElement> query = ElementQuery.ALL_METHODS.onlyAccessible()
                                                .named(name);

        if (callee instanceof TypeIdentifier) {
            query = query.onlyStatic();
        }

        return query;
    }
}

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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.expressions.context.ExpressionEvaluationContext;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.util.List;

import static java.util.Collections.emptyList;

/**
 * Expression AST node used for context element access.
 * Either evaluation context method element, property element or method argument can
 * be accessed. When method is accessed it is clear at AST building stage,
 * but whether property or method argument is accessed is unclear until type resolution against
 * evaluation context is executed. This node checks evaluation context to resolve
 * concrete node type, instantiates respective node and delegates type resolution
 * and bytecode generation to this node
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class ContextElementAccess extends ExpressionNode {

    private final String name;

    private ExpressionNode contextOperation;

    public ContextElementAccess(String name) {
        this.name = name;
    }

    @Override
    protected ExpressionDef generateExpression(ExpressionCompilationContext ctx) {
        return contextOperation.compile(ctx);
    }

    @Override
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        return resolveContextOperation(ctx).resolveClassElement(ctx);
    }

    @Override
    public TypeDef doResolveType(@NonNull ExpressionVisitorContext ctx) {
        return resolveContextOperation(ctx).resolveType(ctx);
    }

    private ExpressionNode resolveContextOperation(ExpressionVisitorContext ctx) {
        if (contextOperation != null) {
            return contextOperation;
        }

        ExpressionEvaluationContext evaluationContext = ctx.evaluationContext();

        List<PropertyElement> propertyElements = evaluationContext.findProperties(name);
        List<ParameterElement> parameterElements = evaluationContext.findParameters(name);

        int totalElements = propertyElements.size() + parameterElements.size();

        if (totalElements == 0) {
            throw new ExpressionCompilationException(
                "No element with name [" + name + "] available in evaluation context");
        } else if (totalElements > 1) {
            throw new ExpressionCompilationException(
                "Ambiguous expression evaluation context reference. Found " + totalElements +
                    " elements with name [" + name + "]");
        }

        if (!propertyElements.isEmpty()) {
            PropertyElement property = propertyElements.iterator().next();
            String readMethodName =
                property.getReadMethod()
                    .orElseThrow(() -> new ExpressionCompilationException(
                        "Failed to obtain read method for property [" + name + "]"))
                    .getName();

            contextOperation = new ContextMethodCall(readMethodName, emptyList());
        } else {
            ParameterElement parameter = parameterElements.iterator().next();
            contextOperation = new ContextMethodParameterAccess(parameter);
        }

        return contextOperation;
    }
}

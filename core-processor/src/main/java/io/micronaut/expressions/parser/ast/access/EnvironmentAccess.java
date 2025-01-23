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
import io.micronaut.core.expressions.ExpressionEvaluationContext;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.lang.reflect.Method;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.STRING;

/**
 * Expression AST node used for retrieving properties from environment at runtime.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class EnvironmentAccess extends ExpressionNode {

    private static final ClassElement STRING_ELEMENT = ClassElement.of(String.class);

    private static final Method GET_PROPERTY_METHOD =
        ReflectionUtils.getRequiredMethod(ExpressionEvaluationContext.class, "getProperty", String.class);

    private final ExpressionNode propertyName;

    public EnvironmentAccess(ExpressionNode propertyName) {
        this.propertyName = propertyName;
    }

    @Override
    protected ExpressionDef generateExpression(ExpressionCompilationContext ctx) {
        return ctx.expressionEvaluationContextVar()
            .invoke(GET_PROPERTY_METHOD, propertyName.compile(ctx));
    }

    @Override
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        resolveType(ctx);
        return ctx.visitorContext().getClassElement(String.class).orElse(STRING_ELEMENT);
    }

    @Override
    protected TypeDef doResolveType(@NonNull ExpressionVisitorContext ctx) {
        TypeDef propertyNameType = propertyName.resolveType(ctx);
        if (!propertyNameType.equals(STRING)) {
            throw new ExpressionCompilationException("Invalid environment access operation. The expression inside environment " +
                "access must resolve to String value of property name");
        }

        // Property value is always returned as string
        return STRING;
    }

}

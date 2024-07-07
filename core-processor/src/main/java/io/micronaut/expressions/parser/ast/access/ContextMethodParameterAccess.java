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
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.ast.util.TypeDescriptors;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.EVALUATION_CONTEXT_TYPE;
import static io.micronaut.inject.processing.JavaModelUtils.getTypeReference;

/**
 * Expression AST node used for context method parameter access.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
final class ContextMethodParameterAccess extends ExpressionNode {

    private static final Method GET_ARGUMENT_METHOD =
        new Method("getArgument", Type.getType(Object.class),
            new Type[] {TypeDescriptors.INT});

    private final ParameterElement parameterElement;

    private Integer parameterIndex;

    public ContextMethodParameterAccess(ParameterElement parameterElement) {
        this.parameterElement = parameterElement;
    }

    @Override
    protected void generateBytecode(@NonNull ExpressionCompilationContext ctx) {
        GeneratorAdapter mv = ctx.methodVisitor();
        mv.loadArg(0);
        mv.push(parameterIndex);
        // invoke getArgument method
        mv.invokeInterface(EVALUATION_CONTEXT_TYPE, GET_ARGUMENT_METHOD);
        if (nodeType != null) {
            if (TypeDescriptors.isPrimitive(nodeType)) {
                mv.unbox(nodeType);
            } else {
                mv.checkCast(nodeType);
            }
        }
    }

    @Override
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        String parameterName = parameterElement.getName();
        ParameterElement[] methodParameters = parameterElement.getMethodElement().getParameters();

        Integer paramIndex = null;
        for (int i = 0; i < methodParameters.length; i++) {
            ParameterElement methodParameter = methodParameters[i];
            if (methodParameter.getName().equals(parameterName)) {
                paramIndex = i;
                break;
            }
        }

        if (paramIndex == null) {
            throw new ExpressionCompilationException(
                "Can not find parameter with name [" + parameterName + "] in method parameters");
        }

        this.parameterIndex = paramIndex;
        return parameterElement.getGenericType();
    }

    @NonNull
    @Override
    protected Type doResolveType(@NonNull ExpressionVisitorContext ctx) {
        doResolveClassElement(ctx);
        return getTypeReference(parameterElement.getType());
    }
}

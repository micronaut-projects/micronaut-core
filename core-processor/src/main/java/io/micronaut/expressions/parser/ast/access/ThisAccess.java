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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.JavaModelUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.EVALUATION_CONTEXT_TYPE;

/**
 * Enables access to 'this' in non-static contexts.
 */
@Internal
public final class ThisAccess extends ExpressionNode {
    @Override
    protected void generateBytecode(@NonNull ExpressionCompilationContext ctx) {
        GeneratorAdapter mv = ctx.methodVisitor();
        mv.loadArg(0);
        mv.invokeInterface(EVALUATION_CONTEXT_TYPE, new Method("getThis", Type.getType(Object.class), new Type[0]));
        mv.checkCast(resolveType(ctx));
    }

    @Override
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        ClassElement thisType = ctx.evaluationContext().findThis();
        if (thisType == null) {
            throw new ExpressionCompilationException("Cannot reference 'this' from the current context.");

        }
        return thisType;
    }

    @NonNull
    @Override
    protected Type doResolveType(@NonNull ExpressionVisitorContext ctx) {
        return JavaModelUtils.getTypeReference(doResolveClassElement(ctx));
    }
}

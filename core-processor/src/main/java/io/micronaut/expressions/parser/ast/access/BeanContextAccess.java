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
import io.micronaut.expressions.parser.ast.types.TypeIdentifier;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.inject.ast.ClassElement;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.EVALUATION_CONTEXT_TYPE;
import static org.objectweb.asm.Opcodes.CHECKCAST;

/**
 * Expression AST node used for to retrieve beans from bean context.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public class BeanContextAccess extends ExpressionNode {

    private static final Method GET_BEAN_METHOD =
        new Method("getBean", Type.getType(Object.class),
            new Type[]{Type.getType(Class.class)});

    private final TypeIdentifier typeIdentifier;

    public BeanContextAccess(TypeIdentifier typeIdentifier) {
        this.typeIdentifier = typeIdentifier;
    }

    @Override
    protected void generateBytecode(ExpressionCompilationContext ctx) {
        GeneratorAdapter mv = ctx.methodVisitor();
        mv.loadArg(0);

        Type beanType = typeIdentifier.resolveType(ctx);
        mv.push(beanType);

        // invoke getBean method
        mv.invokeInterface(EVALUATION_CONTEXT_TYPE, GET_BEAN_METHOD);

        // cast the return value to the correct type
        mv.visitTypeInsn(CHECKCAST, beanType.getInternalName());
    }

    @Override
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        return typeIdentifier.resolveClassElement(ctx);
    }

    @Override
    protected Type doResolveType(@NonNull ExpressionVisitorContext ctx) {
        return typeIdentifier.doResolveType(ctx);
    }
}

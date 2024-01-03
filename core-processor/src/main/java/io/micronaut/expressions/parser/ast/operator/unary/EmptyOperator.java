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
package io.micronaut.expressions.parser.ast.operator.unary;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The empty operator.
 */
@Internal
public final class EmptyOperator extends UnaryOperator {

    private static final String IS_EMPTY = "isEmpty";

    public EmptyOperator(ExpressionNode operand) {
        super(operand);
    }

    @Override
    protected void generateBytecode(ExpressionCompilationContext ctx) {
        ClassElement type = operand.resolveClassElement(ctx);

        GeneratorAdapter mv = ctx.methodVisitor();

        operand.compile(ctx);
        if (type.isAssignable(CharSequence.class)) {
            mv.invokeStatic(
                Type.getType(StringUtils.class),
                Method.getMethod(
                    ReflectionUtils.getRequiredMethod(
                        StringUtils.class,
                        IS_EMPTY,
                        CharSequence.class
                    )
                )
            );
        } else if (type.isAssignable(Collection.class)) {
            mv.invokeStatic(
                Type.getType(CollectionUtils.class),
                Method.getMethod(
                    ReflectionUtils.getRequiredMethod(
                        CollectionUtils.class,
                        IS_EMPTY,
                        Collection.class
                    )
                )
            );
        } else if (type.isAssignable(Map.class)) {
            mv.invokeStatic(
                Type.getType(CollectionUtils.class),
                Method.getMethod(
                    ReflectionUtils.getRequiredMethod(
                        CollectionUtils.class,
                        IS_EMPTY,
                        Map.class
                    )
                )
            );
        } else if (type.isAssignable(Optional.class)) {
            mv.invokeVirtual(
                Type.getType(Optional.class),
                Method.getMethod(
                    ReflectionUtils.getRequiredMethod(
                        Optional.class,
                        IS_EMPTY
                    )
                )
            );
        } else if (type.isArray() && !type.isPrimitive()) {
            mv.invokeStatic(
                Type.getType(ArrayUtils.class),
                Method.getMethod(
                    ReflectionUtils.getRequiredMethod(
                        ArrayUtils.class,
                        IS_EMPTY,
                        Object[].class
                    )
                )
            );
        } else if (type.isPrimitive()) {
            // primitives are never empty
            mv.push(false);
        } else {
            mv.invokeStatic(
                Type.getType(Objects.class),
                Method.getMethod(
                    ReflectionUtils.getRequiredMethod(
                        Objects.class,
                        "isNull",
                        Object.class
                    )
                )
            );
        }
    }

    @Override
    public Type doResolveType(@NonNull ExpressionVisitorContext ctx) {
        return Type.BOOLEAN_TYPE;
    }

    @Override
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        return PrimitiveElement.BOOLEAN;
    }
}

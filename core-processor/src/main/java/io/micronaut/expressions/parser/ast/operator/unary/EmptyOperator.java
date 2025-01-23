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
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;

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
    protected ExpressionDef generateExpression(ExpressionCompilationContext ctx) {
        ClassElement type = operand.resolveClassElement(ctx);

        ExpressionDef opExp = operand.compile(ctx);
        if (type.isAssignable(CharSequence.class)) {
            return ClassTypeDef.of(StringUtils.class)
                .invokeStatic(
                    ReflectionUtils.getRequiredMethod(
                        StringUtils.class,
                        IS_EMPTY,
                        CharSequence.class
                    ),
                    opExp
                );
        } else if (type.isAssignable(Collection.class)) {
            return ClassTypeDef.of(CollectionUtils.class)
                .invokeStatic(
                    ReflectionUtils.getRequiredMethod(
                        CollectionUtils.class,
                        IS_EMPTY,
                        Collection.class
                    ),
                    opExp
                );
        } else if (type.isAssignable(Map.class)) {
            return ClassTypeDef.of(CollectionUtils.class)
                .invokeStatic(
                    ReflectionUtils.getRequiredMethod(
                        CollectionUtils.class,
                        IS_EMPTY,
                        Map.class
                    ),
                    opExp
                );
        } else if (type.isAssignable(Optional.class)) {
            return opExp.invoke(
                        ReflectionUtils.getRequiredMethod(
                            Optional.class,
                            IS_EMPTY
                        )
                    );
        } else if (type.isArray() && !type.isPrimitive()) {
            return ClassTypeDef.of(ArrayUtils.class)
                .invokeStatic(
                    ReflectionUtils.getRequiredMethod(
                        ArrayUtils.class,
                        IS_EMPTY,
                        Object[].class
                    ),
                    opExp
                );
        } else if (type.isPrimitive()) {
            // primitives are never empty
            return ExpressionDef.falseValue();
        } else {
            return ClassTypeDef.of(Objects.class)
                .invokeStatic(
                    ReflectionUtils.getRequiredMethod(
                        Objects.class,
                        "isNull",
                        Object.class
                    ),
                    opExp
                );
        }
    }

    @Override
    public TypeDef doResolveType(@NonNull ExpressionVisitorContext ctx) {
        return TypeDef.Primitive.BOOLEAN;
    }

    @Override
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        return PrimitiveElement.BOOLEAN;
    }
}

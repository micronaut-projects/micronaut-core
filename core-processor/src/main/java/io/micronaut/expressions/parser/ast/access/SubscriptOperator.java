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
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.processing.JavaModelUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.List;
import java.util.Map;

/**
 * Handles list, map and array de-referencing.
 */
@Internal
public class SubscriptOperator extends ExpressionNode {

    private static final Method LIST_GET_METHOD = Method.getMethod(
        ReflectionUtils.getRequiredMethod(List.class, "get", int.class)
    );
    private static final Method MAP_GET_METHOD = Method.getMethod(
        ReflectionUtils.getRequiredMethod(Map.class, "get", Object.class)
    );

    private final ExpressionNode callee;
    private final ExpressionNode index;
    private boolean isArray = false;
    private boolean isMap = false;

    public SubscriptOperator(ExpressionNode callee, ExpressionNode index) {
        this.callee = callee;
        this.index = index;
    }

    @Override
    protected void generateBytecode(ExpressionVisitorContext ctx) {
        callee.compile(ctx);
        GeneratorAdapter methodVisitor = ctx.methodVisitor();
        ClassElement indexType = index.resolveClassElement(ctx);
        index.compile(ctx);

        if (isMap) {
            if (!indexType.isAssignable(String.class)) {
                throw new ExpressionCompilationException("Invalid subscript operator. Map key must be a string.");
            } else {
                methodVisitor.invokeInterface(
                    Type.getType(Map.class),
                    MAP_GET_METHOD
                );
            }
        } else {
            if (!indexType.equals(PrimitiveElement.INT)) {
                throw new ExpressionCompilationException("Invalid subscript operator. Index must be an integer.");
            }
            if (isArray) {
                methodVisitor.arrayLoad(resolveType(ctx));
            } else {
                methodVisitor.invokeInterface(
                    Type.getType(List.class),
                    LIST_GET_METHOD
                );
            }
        }
        if (!isArray) {
            methodVisitor.checkCast(resolveType(ctx));
        }
    }

    @Override
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        ClassElement classElement = callee.resolveClassElement(ctx);
        this.isArray = classElement.isArray();
        this.isMap = classElement.isAssignable(Map.class);
        if (!isMap && !classElement.isAssignable(List.class) && !isArray) {
            throw new ExpressionCompilationException("Invalid subscript operator. Subscript operator can only be applied to maps, lists and arrays");
        }
        if (isArray) {
            return classElement.fromArray();
        } else if (isMap) {
            Map<String, ClassElement> typeArguments = classElement.getTypeArguments();
            if (typeArguments.containsKey("V")) {
                return typeArguments.get("V");
            } else {
                return ClassElement.of(Object.class);
            }
        } else {
            return classElement.getFirstTypeArgument()
                .orElseGet(() -> ClassElement.of(Object.class));
        }
    }

    @Override
    protected Type doResolveType(ExpressionVisitorContext ctx) {
        ClassElement valueElement = resolveClassElement(ctx);
        return JavaModelUtils.getTypeReference(valueElement);
    }
}

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
package io.micronaut.expressions.parser.ast;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;

/**
 * Abstract evaluated expression AST node.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public abstract class ExpressionNode {

    protected TypeDef nodeType;
    protected ClassElement classElement;

    /**
     * Compiles this expression AST node against passes compilation context.
     * Node compilation includes type resolution and bytecode generation.
     *
     * @param ctx expression compilation context
     * @return The expression definition
     */
    public final ExpressionDef compile(@NonNull ExpressionCompilationContext ctx) {
        resolveType(ctx);
        return generateExpression(ctx);
    }

    /**
     * Generates bytecode for this AST node.
     *
     * @param ctx expression compilation context
     * @return The expression definition
     */
    protected abstract ExpressionDef generateExpression(@NonNull ExpressionCompilationContext ctx);

    /**
     * On resolution stage type information is collected and node validity is checked. Once type
     * is resolved, type resolution result is cached.
     *
     * @param ctx expression compilation context
     *
     * @return resolved type
     */
    @NonNull
    public final TypeDef resolveType(@NonNull ExpressionVisitorContext ctx) {
        if (nodeType == null) {
            nodeType = doResolveType(ctx);
        }
        return nodeType;
    }

    /**
     * On resolution stage type information is collected and node validity is checked. Once type
     * is resolved, type resolution result is cached.
     *
     * @param ctx expression compilation context
     *
     * @return resolved type
     */
    @NonNull
    public final TypeDef resolveType(@NonNull ExpressionCompilationContext ctx) {
        return resolveType(ctx.evaluationVisitorContext());
    }

    /**
     * On resolution stage type information is collected and node validity is checked. Once type
     * is resolved, type resolution result is cached.
     *
     * @param ctx expression compilation context
     *
     * @return resolved type
     */
    @NonNull
    public final ClassElement resolveClassElement(@NonNull ExpressionVisitorContext ctx) {
        if (classElement == null) {
            classElement = doResolveClassElement(ctx);
        }
        return classElement;
    }

    /**
     * On resolution stage type information is collected and node validity is checked. Once type
     * is resolved, type resolution result is cached.
     *
     * @param ctx expression compilation context
     *
     * @return resolved type
     */
    @NonNull
    public final ClassElement resolveClassElement(@NonNull ExpressionCompilationContext ctx) {
        return resolveClassElement(ctx.evaluationVisitorContext());
    }

    /**
     * Resolves the class element for this node.
     * @param ctx The expression compilation context
     * @return The resolved type
     */
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        TypeDef type = doResolveType(ctx);
        if (type instanceof TypeDef.Primitive primitive) {
            return PrimitiveElement.valueOf(primitive.name());
        }
        if (type instanceof ClassTypeDef.ClassElementType classElementType) {
            return classElementType.classElement();
        }
        if (type instanceof ClassTypeDef classTypeDef) {
            return ctx.visitorContext().getClassElement(classTypeDef.getName()).orElseThrow();
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    /**
     * Resolves the class element for this node.
     * @param ctx The expression compilation context
     * @return The resolved type
     */
    protected ClassElement doResolveClassElement(ExpressionCompilationContext ctx) {
        return doResolveClassElement(ctx.evaluationVisitorContext());
    }

    /**
     * Resolves expression AST node type.
     *
     * @param ctx expression compilation context
     *
     * @return resolved type
     */
    @NonNull
    protected TypeDef doResolveType(@NonNull ExpressionVisitorContext ctx) {
        return TypeDef.erasure(doResolveClassElement(ctx));
    }
}

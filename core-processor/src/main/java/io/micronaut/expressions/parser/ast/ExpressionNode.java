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
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import org.objectweb.asm.Type;

/**
 * Abstract evaluated expression AST node.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public abstract class ExpressionNode {

    protected Type nodeType;

    /**
     * Compiles this expression AST node against passes compilation context.
     * Node compilation includes type resolution and bytecode generation.
     *
     * @param ctx expression compilation context
     */
    public final void compile(@NonNull ExpressionVisitorContext ctx) {
        resolveType(ctx);
        generateBytecode(ctx);
    }

    /**
     * Generates bytecode for this AST node.
     *
     * @param ctx expression compilation context
     */
    protected abstract void generateBytecode(@NonNull ExpressionVisitorContext ctx);

    /**
     * On resolution stage type information is collected and node validity is checked. Once type
     * is resolved, type resolution result is cached.
     *
     * @param ctx expression compilation context
     *
     * @return resolved type
     */
    @NonNull
    public final Type resolveType(@NonNull ExpressionVisitorContext ctx) {
        if (nodeType == null) {
            nodeType = doResolveType(ctx);
        }
        return nodeType;
    }

    /**
     * Resolves expression AST node type.
     *
     * @param ctx expression compilation context
     *
     * @return resolved type
     */
    @NonNull
    protected abstract Type doResolveType(@NonNull ExpressionVisitorContext ctx);
}

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
package io.micronaut.expressions.parser.ast.literal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.inject.ast.ClassElement;
import org.objectweb.asm.Type;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.STRING;

/**
 * Expression AST node for string literal.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class StringLiteral extends ExpressionNode {

    private static final ClassElement STRING_ELEMENT = ClassElement.of(String.class);
    private final String value;

    public StringLiteral(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public void generateBytecode(ExpressionVisitorContext ctx) {
        ctx.methodVisitor().push(value);
    }

    @Override
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        return ctx.visitorContext().getClassElement(String.class).orElse(STRING_ELEMENT);
    }

    @Override
    protected Type doResolveType(ExpressionVisitorContext ctx) {
        return STRING;
    }
}

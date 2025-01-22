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
package io.micronaut.expressions.parser.ast.operator.binary;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.ast.literal.StringLiteral;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.BOOLEAN;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.STRING;

/**
 * Expression AST node for regex 'matches' operator.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class MatchesOperator extends ExpressionNode {

    private static final java.lang.reflect.Method MATCHES = ReflectionUtils.getRequiredMethod(
        String.class, "matches", String.class
    );

    private final ExpressionNode operand;
    private final StringLiteral pattern;

    public MatchesOperator(ExpressionNode operand, StringLiteral pattern) {
        this.operand = operand;
        this.pattern = pattern;
    }

    @Override
    public ExpressionDef generateExpression(ExpressionCompilationContext ctx) {
        return operand.compile(ctx)
            .invoke(MATCHES, pattern.compile(ctx));
    }

    @Override
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        return PrimitiveElement.BOOLEAN;
    }

    @Override
    protected TypeDef doResolveType(@NonNull ExpressionVisitorContext ctx) {
        if (!operand.resolveType(ctx).equals(STRING)) {
            throw new ExpressionCompilationException(
                "Operator 'matches' can only be applied to String operand");
        }

        String patternValue = pattern.getValue();
        try {
            Pattern.compile(patternValue);
        } catch (PatternSyntaxException ex) {
            throw new ExpressionCompilationException("Invalid RegEx pattern provided: " + patternValue);
        }

        return BOOLEAN;
    }
}

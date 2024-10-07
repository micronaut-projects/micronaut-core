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
package io.micronaut.expressions.parser;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.ast.collection.OneDimensionalArray;
import io.micronaut.expressions.parser.ast.literal.StringLiteral;
import io.micronaut.expressions.parser.ast.operator.binary.AddOperator;
import io.micronaut.expressions.parser.ast.types.TypeIdentifier;
import io.micronaut.expressions.parser.exception.ExpressionParsingException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.micronaut.expressions.EvaluatedExpressionConstants.EXPRESSION_PREFIX;


/**
 * This parser is used to split complex expression into multiple
 * single expressions if necessary and delegate each atomic expression
 * parsing to separate instance of {@link SingleEvaluatedExpressionParser},
 * then combining single expressions parsing results.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class CompoundEvaluatedExpressionParser implements EvaluatedExpressionParser {

    private final Object expression;

    /**
     * Instantiates compound expression parser.
     *
     * @param expression either string or string[]
     */
    public CompoundEvaluatedExpressionParser(@NonNull Object expression) {
        if (!(expression instanceof String || expression instanceof String[])) {
            throw new ExpressionParsingException("Can not parse expression: " + expression);
        }

        this.expression = expression;
    }

    @Override
    public ExpressionNode parse() throws ExpressionParsingException {
        // if expression doesn't have prefix, the whole string is treated as expression
        if (expression instanceof String str && !str.contains(EXPRESSION_PREFIX)) {
            return new SingleEvaluatedExpressionParser(str).parse();
        }

        return parseTemplateExpression(expression);
    }

    private ExpressionNode parseTemplateExpression(Object expression) {
        if (expression instanceof String str) {
            List<ExpressionNode> expressionParts =
                splitExpressionParts(str).stream()
                    .map(this::prepareExpressionPart)
                    .map(SingleEvaluatedExpressionParser::new)
                    .map(SingleEvaluatedExpressionParser::parse)
                    .toList();

            if (expressionParts.size() == 1) {
                return expressionParts.get(0);
            } else {
                return expressionParts.stream()
                           .reduce(new StringLiteral(""), AddOperator::new);
            }
        } else {
            List<ExpressionNode> arrayNodes =
                Arrays.stream((String[]) expression)
                    .map(this::parseTemplateExpression)
                    .toList();

            return buildArrayOfExpressions(arrayNodes);
        }
    }

    private ExpressionNode buildArrayOfExpressions(List<ExpressionNode> nodes) {
        TypeIdentifier arrayElementType = new TypeIdentifier("Object");
        return new OneDimensionalArray(arrayElementType, nodes);
    }

    private List<String> splitExpressionParts(String expression) {
        List<String> parts = new ArrayList<>();
        String nextPart = nextPart(expression);
        while (!nextPart.isEmpty()) {
            parts.add(nextPart);
            expression = expression.substring(nextPart.length());
            nextPart = nextPart(expression);
        }

        return parts;
    }

    private String nextPart(String expression) {
        if (expression.startsWith(EXPRESSION_PREFIX)) {
            int unbalancedParenthesis = 1;

            StringBuilder expressionPart = new StringBuilder(EXPRESSION_PREFIX);
            int pointer = EXPRESSION_PREFIX.length();
            while (unbalancedParenthesis > 0 && pointer < expression.length()) {
                char nextChar = expression.charAt(pointer++);
                expressionPart.append(nextChar);
                if (nextChar == '{') {
                    unbalancedParenthesis++;
                } else if (nextChar == '}') {
                    unbalancedParenthesis--;
                }
            }

            if (unbalancedParenthesis > 0) {
                throw new ExpressionParsingException("Unbalanced parenthesis in expression: " + expression);
            }

            return expressionPart.toString();
        } else {
            int substringUntil = expression.contains(EXPRESSION_PREFIX)
                                     ? expression.indexOf(EXPRESSION_PREFIX)
                                     : expression.length();
            return expression.substring(0, substringUntil);
        }
    }

    private String prepareExpressionPart(String expressionPart) {
        if (expressionPart.startsWith(EXPRESSION_PREFIX)) {
            return expressionPart.substring(EXPRESSION_PREFIX.length(),
                expressionPart.length() - 1);
        } else {
            return "'" + expressionPart + "'";
        }
    }
}

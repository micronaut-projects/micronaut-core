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
package io.micronaut.expressions.parser.token;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.expressions.parser.exception.ExpressionParsingException;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.micronaut.expressions.parser.token.TokenType.AND;
import static io.micronaut.expressions.parser.token.TokenType.BEAN_CONTEXT;
import static io.micronaut.expressions.parser.token.TokenType.BOOL;
import static io.micronaut.expressions.parser.token.TokenType.COLON;
import static io.micronaut.expressions.parser.token.TokenType.COMMA;
import static io.micronaut.expressions.parser.token.TokenType.DECREMENT;
import static io.micronaut.expressions.parser.token.TokenType.DIV;
import static io.micronaut.expressions.parser.token.TokenType.DOT;
import static io.micronaut.expressions.parser.token.TokenType.DOUBLE;
import static io.micronaut.expressions.parser.token.TokenType.ELVIS;
import static io.micronaut.expressions.parser.token.TokenType.EMPTY;
import static io.micronaut.expressions.parser.token.TokenType.ENVIRONMENT;
import static io.micronaut.expressions.parser.token.TokenType.EQ;
import static io.micronaut.expressions.parser.token.TokenType.EXPRESSION_CONTEXT_REF;
import static io.micronaut.expressions.parser.token.TokenType.FLOAT;
import static io.micronaut.expressions.parser.token.TokenType.GT;
import static io.micronaut.expressions.parser.token.TokenType.GTE;
import static io.micronaut.expressions.parser.token.TokenType.IDENTIFIER;
import static io.micronaut.expressions.parser.token.TokenType.INCREMENT;
import static io.micronaut.expressions.parser.token.TokenType.INSTANCEOF;
import static io.micronaut.expressions.parser.token.TokenType.INT;
import static io.micronaut.expressions.parser.token.TokenType.LONG;
import static io.micronaut.expressions.parser.token.TokenType.LT;
import static io.micronaut.expressions.parser.token.TokenType.LTE;
import static io.micronaut.expressions.parser.token.TokenType.L_CURLY;
import static io.micronaut.expressions.parser.token.TokenType.L_PAREN;
import static io.micronaut.expressions.parser.token.TokenType.L_SQUARE;
import static io.micronaut.expressions.parser.token.TokenType.MATCHES;
import static io.micronaut.expressions.parser.token.TokenType.MINUS;
import static io.micronaut.expressions.parser.token.TokenType.MOD;
import static io.micronaut.expressions.parser.token.TokenType.MUL;
import static io.micronaut.expressions.parser.token.TokenType.NE;
import static io.micronaut.expressions.parser.token.TokenType.NOT;
import static io.micronaut.expressions.parser.token.TokenType.NULL;
import static io.micronaut.expressions.parser.token.TokenType.OR;
import static io.micronaut.expressions.parser.token.TokenType.PLUS;
import static io.micronaut.expressions.parser.token.TokenType.POW;
import static io.micronaut.expressions.parser.token.TokenType.QMARK;
import static io.micronaut.expressions.parser.token.TokenType.R_CURLY;
import static io.micronaut.expressions.parser.token.TokenType.R_PAREN;
import static io.micronaut.expressions.parser.token.TokenType.R_SQUARE;
import static io.micronaut.expressions.parser.token.TokenType.SAFE_NAV;
import static io.micronaut.expressions.parser.token.TokenType.STRING;
import static io.micronaut.expressions.parser.token.TokenType.THIS;
import static io.micronaut.expressions.parser.token.TokenType.TYPE_IDENTIFIER;
import static io.micronaut.expressions.parser.token.TokenType.WHITESPACE;

/**
 * Tokenizer for parsing evaluated expressions.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class Tokenizer {

    private static final Map<String, TokenType> TOKENS = CollectionUtils.mapOf(
        // WHITESPACES
        "^\\s+", WHITESPACE,

        // BRACES
        "^\\{", L_CURLY,
        "^}", R_CURLY,
        "^\\[", L_SQUARE,
        "^]", R_SQUARE,
        "^\\(", L_PAREN,
        "^\\)", R_PAREN,

        // KEYWORDS
        "^instanceof\\b", INSTANCEOF,
        "^matches\\b", MATCHES,
        "^empty\\b", EMPTY,
        "^ctx\\b", BEAN_CONTEXT,
        "^env\\b", ENVIRONMENT,
        "^this\\b", THIS,

        // LITERALS
        "^null\\b", NULL,          // NULL
        "^(true|false)\\b", BOOL,  // BOOLEAN
        "^'[^']*'", STRING,        // STRING
        // FLOAT
        "^\\d+\\.\\d*((e|E)(\\+|-)?\\d+)?(f|F)", FLOAT,
        "^\\.\\d+((e|E)(\\+|-)?\\d+)?(f|F)", FLOAT,
        "^\\d+((e|E)(\\+|-)?\\d+)?(f|F)", FLOAT,
        // DOUBLE
        "^\\d+\\.\\d*((e|E)(\\+|-)?\\d+)?(d|D)?", DOUBLE,
        "^\\.\\d+((e|E)(\\+|-)?\\d+)?(d|D)?", DOUBLE,
        "^\\d+((e|E)(\\+|-)?\\d+)(d|D)?", DOUBLE,
        "^\\d+((e|E)(\\+|-)?\\d+)?(d|D)", DOUBLE,
        // LONG
        "^0(x|X)[0-9a-fA-F]+(l|L)", LONG,
        "^\\d+(l|L)", LONG,
        // INT
        "^0(x|X)[0-9a-fA-F]+", INT,
        "^\\d+", INT,

        // SYMBOLS
        "^#", EXPRESSION_CONTEXT_REF,
        "^\\?\\.", SAFE_NAV,
        "^\\?\\:", ELVIS,
        "^\\?", QMARK,
        "^\\.", DOT,
        "^,", COMMA,
        "^\\:", COLON,

        // RELATIONAL OPERATORS
        "^==", EQ,
        "^!=", NE,
        "^>=", GTE,
        "^>", GT,
        "^<=", LTE,
        "^<", LT,

        // LOGICAL OPERATORS
        "^!", NOT,
        "^not\\b", NOT,
        "^&&", AND,
        "^and\\b", AND,
        "^\\|\\|", OR,
        "^or\\b", OR,

        // MATH OPERATORS
        "^\\+\\+", INCREMENT,
        "^\\+", PLUS,
        "^\\-\\-", DECREMENT,
        "^\\-", MINUS,
        "^\\*", MUL,
        "^/", DIV,
        "^div\\b", DIV,
        "^%", MOD,
        "^mod\\b", MOD,
        "^\\^", POW,

        // IDENTIFIERS
        "^T\\(", TYPE_IDENTIFIER,
        "\\w+", IDENTIFIER);

    private static final List<TokenPattern> PATTERNS =
        TOKENS.entrySet()
            .stream()
            .map(entry -> TokenPattern.of(entry.getKey(), entry.getValue()))
            .toList();

    private final int length;
    private final String expression;

    private int cursor;
    private String remaining;

    public Tokenizer(String expression) {
        this.expression = expression;
        this.remaining = expression;
        this.cursor = 0;
        this.length = expression.length();
    }

    @Nullable
    public Token getNextToken() {
        if (!hasMoreTokens()) {
            return null;
        }

        remaining = expression.substring(cursor);
        for (TokenPattern pattern : PATTERNS) {
            Token token = pattern.matches(remaining);
            if (token == null) {
                continue;
            }

            cursor += token.value().length();

            if (token.type() == WHITESPACE) {
                return getNextToken();
            }

            return token;
        }

        throw new ExpressionParsingException("Unexpected token: " + remaining);
    }

    private boolean hasMoreTokens() {
        return cursor < length;
    }

    private record TokenPattern(Pattern pattern, TokenType tokenType) {

        public static TokenPattern of(String pattern, TokenType tokenType) {
            return new TokenPattern(Pattern.compile(pattern), tokenType);
        }

        @Nullable
        public Token matches(String value) {
            Matcher matcher = pattern.matcher(value);
            if (!matcher.find()) {
                return null;
            }

            return new Token(tokenType, matcher.group());
        }
    }
}

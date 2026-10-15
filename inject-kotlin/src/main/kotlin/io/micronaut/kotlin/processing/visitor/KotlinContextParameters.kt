/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Origin
import java.io.File
import java.io.IOException

/**
 * Finds the functions and properties that declare Kotlin context parameters, such as
 * `context(log: Logger) fun ctx(a: String)`.
 *
 * KSP 2.3 has no API for context parameters: `KSFunctionDeclaration.parameters`, `asMemberOf` and
 * `Resolver.mapToJvmSignature` all leave them out, although the JVM method takes them as its leading
 * parameters. Until KSP exposes them, the declaration's source is read at the [FileLocation] KSP reports,
 * which is the line of the declaration's name, and the modifiers in front of the name are checked for a
 * `context(...)` list. The source is tokenized, so comments, string literals, annotations and modifiers
 * spread over several lines do not matter.
 *
 * Only declarations compiled from Kotlin sources ([Origin.KOTLIN]) have a source to read. A declaration
 * of a compiled class on the classpath ([Origin.KOTLIN_LIB]) has no file location and is reported as
 * declaring none.
 *
 * Instances are bound to a [KotlinVisitorContext], and cache the tokens of every file read.
 */
internal class KotlinContextParameters {

    private val sources: MutableMap<String, KotlinSourceTokens> = HashMap()

    /**
     * Whether the function or property declares context parameters.
     *
     * @param declaration The declaration
     * @return `true` if its source declares a `context(...)` list, `false` if it does not or if it has no source
     */
    fun declares(declaration: KSDeclaration): Boolean {
        if (declaration.origin != Origin.KOTLIN) {
            return false
        }
        val keywords = when (declaration) {
            is KSFunctionDeclaration -> FUNCTION_KEYWORDS
            is KSPropertyDeclaration -> PROPERTY_KEYWORDS
            else -> return false
        }
        val location = declaration.location as? FileLocation ?: return false
        val tokens = sources.getOrPut(location.filePath) { KotlinSourceTokens.read(location.filePath) }
        return tokens.declaresContextParameters(location.lineNumber, declaration.simpleName.asString(), keywords)
    }

    private companion object {
        val FUNCTION_KEYWORDS = setOf("fun")
        val PROPERTY_KEYWORDS = setOf("val", "var")
    }
}

/**
 * The tokens of a Kotlin source file, without whitespace and comments. A string literal is a single token,
 * including the expressions of its templates.
 */
internal class KotlinSourceTokens private constructor(private val tokens: List<SourceToken>) {

    // The indexes of the identifier tokens of each line
    private val identifiersByLine: Map<Int, List<Int>> = tokens.indices
        .filter { tokens[it].isIdentifier() }
        .groupBy { tokens[it].line }

    /**
     * Whether the declaration whose name is on the given line declares context parameters.
     *
     * @param line The line of the declaration's name, starting at 1
     * @param name The declaration's name
     * @param keywords The keywords that introduce the declaration, `fun` or `val` and `var`
     * @return `true` if the modifiers of the declaration include a `context(...)` list
     */
    fun declaresContextParameters(line: Int, name: String, keywords: Set<String>): Boolean {
        for (index in identifiersByLine[line] ?: return false) {
            if (tokens[index].text != name) {
                continue
            }
            val keyword = findDeclarationKeyword(index) ?: continue
            if (tokens[keyword].text in keywords) {
                return hasContextModifier(keyword)
            }
        }
        return false
    }

    /**
     * Finds the `fun`, `val` or `var` keyword of a declaration name: the name follows the keyword, or a
     * receiver type (`fun String.name`) or type parameters (`fun <T> name`) that follow it.
     */
    private fun findDeclarationKeyword(nameIndex: Int): Int? {
        val previous = tokens.getOrNull(nameIndex - 1) ?: return null
        if (!previous.isKeyword(DECLARATION_KEYWORDS) && !previous.isSymbol(".") && !previous.isSymbol(">")) {
            return null
        }
        var depth = 0
        var index = nameIndex - 1
        var steps = 0
        while (index >= 0 && steps++ < MAX_HEADER_TOKENS) {
            val token = tokens[index]
            when {
                token.isSymbol(")") || token.isSymbol(">") || token.isSymbol("]") -> depth++
                token.isSymbol("(") || token.isSymbol("<") || token.isSymbol("[") -> {
                    depth--
                    if (depth < 0) {
                        return null
                    }
                }
                depth == 0 && token.isKeyword(DECLARATION_KEYWORDS) -> return index
                depth == 0 && (token.isSymbol(";") || token.isSymbol("{") || token.isSymbol("}") || token.isSymbol("=")) -> return null
            }
            index--
        }
        return null
    }

    /**
     * Walks back from the declaration keyword over its modifiers and annotations, looking for `context(...)`.
     */
    private fun hasContextModifier(keywordIndex: Int): Boolean {
        var index = keywordIndex - 1
        while (index >= 0) {
            val token = tokens[index]
            index = when {
                token.isKeyword(MODIFIERS) -> index - 1
                token.isSymbol(")") -> {
                    val open = matchBackward(index, "(", ")") ?: return false
                    val callee = open - 1
                    if (callee >= 0 && tokens[callee].isKeyword(CONTEXT) && startsModifier(callee - 1)) {
                        return true
                    }
                    // the arguments of an annotation
                    annotationStart(callee) ?: return false
                }
                token.isSymbol("]") -> {
                    val open = matchBackward(index, "[", "]") ?: return false
                    annotationPrefix(open - 1) ?: return false
                }
                token.isIdentifier() -> annotationStart(index) ?: return false
                else -> return false
            }
        }
        return false
    }

    /**
     * Whether a token can precede a modifier: the start of the file, the end of the previous declaration
     * or statement, or another modifier or annotation. An operator, a dot, a colon or an opening bracket
     * means that `context(...)` is an expression or a type instead.
     */
    private fun startsModifier(index: Int): Boolean {
        val token = tokens.getOrNull(index) ?: return true
        return when (token.kind) {
            SourceTokenKind.SYMBOL -> when (token.text) {
                "{", "}", ";", ")", "]", "?" -> true
                ">" -> closesTypeArguments(index)
                else -> false
            }
            else -> true
        }
    }

    /**
     * Whether the `>` at the index closes the type arguments of a type, as in `fun list(): List<String>`,
     * rather than being a comparison.
     */
    private fun closesTypeArguments(closeIndex: Int): Boolean {
        var depth = 0
        var index = closeIndex
        while (index >= 0) {
            val token = tokens[index]
            if (token.isSymbol(">")) {
                depth++
            } else if (token.isSymbol("<")) {
                depth--
                if (depth == 0) {
                    return tokens.getOrNull(index - 1)?.isIdentifier() == true
                }
            } else if (!token.isIdentifier() && !(token.kind == SourceTokenKind.SYMBOL && token.text in TYPE_SYMBOLS)) {
                return false
            }
            index--
        }
        return false
    }

    /**
     * Walks back over an annotation name ending at the index, `@Name`, `@a.b.Name` or `@get:Name`.
     *
     * @return The index of the token before the annotation, or `null` if there is no annotation
     */
    private fun annotationStart(nameIndex: Int): Int? {
        if (tokens.getOrNull(nameIndex)?.isIdentifier() != true) {
            return null
        }
        var index = nameIndex
        while (index >= 2 && tokens[index - 1].isSymbol(".") && tokens[index - 2].isIdentifier()) {
            index -= 2
        }
        return annotationPrefix(index - 1)
    }

    /**
     * Walks back over the `@` or `@target:` in front of an annotation name or an `@[...]` list.
     *
     * @return The index of the token before the annotation, or `null` if there is no annotation
     */
    private fun annotationPrefix(index: Int): Int? {
        val token = tokens.getOrNull(index) ?: return null
        if (token.isSymbol("@")) {
            return index - 1
        }
        if (token.isSymbol(":") && tokens.getOrNull(index - 1)?.isIdentifier() == true && tokens.getOrNull(index - 2)?.isSymbol("@") == true) {
            return index - 3
        }
        return null
    }

    private fun matchBackward(closeIndex: Int, open: String, close: String): Int? {
        var depth = 0
        var index = closeIndex
        while (index >= 0) {
            val token = tokens[index]
            if (token.isSymbol(close)) {
                depth++
            } else if (token.isSymbol(open)) {
                depth--
                if (depth == 0) {
                    return index
                }
            }
            index--
        }
        return null
    }

    internal companion object {
        private const val CONTEXT = "context"
        private const val MAX_HEADER_TOKENS = 256
        private val EMPTY = KotlinSourceTokens(emptyList())
        private val DECLARATION_KEYWORDS = setOf("fun", "val", "var")
        private val MODIFIERS = setOf(
            "public", "protected", "private", "internal",
            "open", "final", "abstract", "override", "sealed",
            "inner", "data", "enum", "annotation", "companion", "value",
            "inline", "noinline", "crossinline", "tailrec", "operator", "infix", "external", "suspend",
            "const", "lateinit", "expect", "actual", "vararg", "reified"
        )
        private val TYPE_SYMBOLS = setOf(".", ",", "?", "*", "(", ")", "->", ":", "@", "<", ">")

        /**
         * Reads and tokenizes a source file.
         *
         * @param path The path of the file
         * @return The tokens, no tokens if the file cannot be read or never mentions `context`
         */
        fun read(path: String): KotlinSourceTokens {
            val source = try {
                File(path).readText()
            } catch (_: IOException) {
                return EMPTY
            }
            return of(source)
        }

        /**
         * Tokenizes a source.
         *
         * @param source The Kotlin source
         * @return The tokens, no tokens if the source never mentions `context`
         */
        fun of(source: String): KotlinSourceTokens {
            if (!source.contains(CONTEXT)) {
                return EMPTY
            }
            return KotlinSourceTokens(KotlinLexer(source).tokenize())
        }
    }
}

internal enum class SourceTokenKind {
    IDENTIFIER,
    // An identifier in backticks, which is never a keyword
    QUOTED_IDENTIFIER,
    LITERAL,
    SYMBOL
}

internal class SourceToken(val kind: SourceTokenKind, val text: String, val line: Int) {

    fun isIdentifier() = kind == SourceTokenKind.IDENTIFIER || kind == SourceTokenKind.QUOTED_IDENTIFIER

    fun isKeyword(keywords: Set<String>) = kind == SourceTokenKind.IDENTIFIER && text in keywords

    fun isKeyword(keyword: String) = kind == SourceTokenKind.IDENTIFIER && text == keyword

    fun isSymbol(symbol: String) = kind == SourceTokenKind.SYMBOL && text == symbol

    override fun toString() = "$text@$line"
}

/**
 * A lexer for the subset of Kotlin needed to find modifiers: identifiers, symbols, literals and the
 * string templates and comments that must not be read as code.
 */
private class KotlinLexer(private val source: String) {

    private val tokens: MutableList<SourceToken> = ArrayList()
    private var position = 0
    private var line = 1
    // Greater than zero inside the template expressions of a string literal, whose tokens are dropped
    private var templateDepth = 0

    fun tokenize(): List<SourceToken> {
        if (source.startsWith("#!")) {
            skipLine()
        }
        code(inTemplate = false)
        return tokens
    }

    /**
     * Lexes code until the end of the source, or, inside a string template, until the brace closing it.
     */
    private fun code(inTemplate: Boolean) {
        var braces = 0
        while (position < source.length) {
            val c = source[position]
            if (inTemplate && c == '}' && braces == 0) {
                // The brace closing the template
                position++
                return
            }
            if (inTemplate) {
                braces += braceDelta(c)
            }
            token(c)
        }
    }

    private fun braceDelta(c: Char) = when (c) {
        '{' -> 1
        '}' -> -1
        else -> 0
    }

    /**
     * Lexes the token, comment or whitespace starting with the given character.
     */
    private fun token(c: Char) {
        when {
            c == '\n' -> {
                line++
                position++
            }
            c.isWhitespace() -> position++
            c == '/' && peek(1) == '/' -> skipLine()
            c == '/' && peek(1) == '*' -> skipBlockComment()
            c == '"' -> string(1)
            c == '$' && isMultiDollarString() -> {
                val dollars = countDollars()
                position += dollars
                string(dollars)
            }
            c == '\'' -> charLiteral()
            c == '`' -> quotedIdentifier()
            c == '_' || c.isLetter() -> identifier()
            c.isDigit() -> number()
            else -> symbol()
        }
    }

    private fun peek(offset: Int): Char? = source.getOrNull(position + offset)

    private fun add(kind: SourceTokenKind, text: String, line: Int) {
        if (templateDepth == 0) {
            tokens.add(SourceToken(kind, text, line))
        }
    }

    private fun skipLine() {
        while (position < source.length && source[position] != '\n') {
            position++
        }
    }

    private fun skipBlockComment() {
        // Kotlin block comments nest
        var depth = 0
        while (position < source.length) {
            if (source.startsWith("/*", position)) {
                depth++
                position += 2
            } else if (source.startsWith("*/", position)) {
                depth--
                position += 2
                if (depth == 0) {
                    return
                }
            } else {
                advance()
            }
        }
    }

    private fun advance() {
        if (source[position] == '\n') {
            line++
        }
        position++
    }

    private fun countDollars(): Int {
        var count = 0
        while (peek(count) == '$') {
            count++
        }
        return count
    }

    private fun isMultiDollarString() = peek(countDollars()) == '"'

    /**
     * Lexes a string literal starting at the opening quote. A template is introduced by as many dollars as
     * the literal is prefixed with, one for a plain literal.
     */
    private fun string(dollars: Int) {
        val startLine = line
        val raw = source.startsWith("\"\"\"", position)
        position += if (raw) 3 else 1
        while (position < source.length) {
            if (closesString(raw)) {
                break
            }
            stringContent(raw, dollars)
        }
        add(SourceTokenKind.LITERAL, "\"", startLine)
    }

    /**
     * Consumes the closing quotes if the string literal ends at the current position.
     */
    private fun closesString(raw: Boolean): Boolean {
        if (raw && source.startsWith("\"\"\"", position)) {
            // A raw string can end with more quotes, the last three close it
            while (peek(0) == '"') {
                position++
            }
            return true
        }
        if (!raw && source[position] == '"') {
            position++
            return true
        }
        return false
    }

    /**
     * Lexes the escape, template or character of a string literal at the current position.
     */
    private fun stringContent(raw: Boolean, dollars: Int) {
        val c = source[position]
        if (!raw && c == '\\') {
            position++
            if (position < source.length) {
                advance()
            }
        } else if (c == '$' && countDollars() == dollars && peek(dollars) == '{') {
            position += dollars + 1
            templateDepth++
            code(inTemplate = true)
            templateDepth--
        } else {
            advance()
        }
    }

    private fun charLiteral() {
        position++
        while (position < source.length && source[position] != '\'' && source[position] != '\n') {
            if (source[position] == '\\') {
                position++
            }
            position++
        }
        position++
        add(SourceTokenKind.LITERAL, "'", line)
    }

    private fun quotedIdentifier() {
        val start = ++position
        while (position < source.length && source[position] != '`' && source[position] != '\n') {
            position++
        }
        add(SourceTokenKind.QUOTED_IDENTIFIER, source.substring(start, position.coerceAtMost(source.length)), line)
        position++
    }

    private fun identifier() {
        val start = position
        while (position < source.length && (source[position] == '_' || source[position].isLetterOrDigit())) {
            position++
        }
        add(SourceTokenKind.IDENTIFIER, source.substring(start, position), line)
    }

    private fun number() {
        val start = position
        while (position < source.length) {
            val c = source[position]
            if (c == '_' || c.isLetterOrDigit() || (c == '.' && peek(1)?.isDigit() == true)) {
                position++
            } else {
                break
            }
        }
        add(SourceTokenKind.LITERAL, source.substring(start, position), line)
    }

    private fun symbol() {
        if (source.startsWith("->", position)) {
            add(SourceTokenKind.SYMBOL, "->", line)
            position += 2
        } else {
            add(SourceTokenKind.SYMBOL, source[position].toString(), line)
            position++
        }
    }
}

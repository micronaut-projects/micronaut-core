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

/**
 * A single KDoc/Javadoc block tag, e.g. `@property percentage the VAT rate`.
 *
 * @property tag the tag keyword without the leading `@` (`"property"`, `"param"`, `"return"`, ...)
 * @property name the tag's target name for tags that document a named element
 *                (`@param`/`@property`), otherwise `null`
 * @property content the remaining tag text, with any continuation lines joined by a single space
 */
internal data class BlockTag(val tag: String, val name: String?, val content: String)

/**
 * A KDoc string split into its leading prose [description] and its trailing [blockTags].
 *
 * @property description the prose preceding the first block tag, trimmed (empty when the doc opens with a tag)
 * @property blockTags the block tags in declaration order
 */
internal data class ParsedKDoc(val description: String, val blockTags: List<BlockTag>) {

    /**
     * Returns the documentation for the constructor parameter [name].
     *
     * @return the matching tag content, or `null` if no `@property`/`@param` tag documents [name]
     */
    fun parameterDoc(name: String): String? =
        tagContent("property", name) ?: tagContent("param", name)

    private fun tagContent(tag: String, name: String): String? =
        blockTags.firstOrNull { it.tag == tag && it.name == name && it.content.isNotBlank() }?.content
}

// A block tag opens on a line whose first non-blank character is '@'; group 1 is the tag
// keyword, group 2 the rest of that line. Everything before the first such line is prose.
private val TAG_LINE = Regex("""^\s*@(\w+)\s*(.*)$""")

// Tags whose first token after the keyword is the name of the element they document.
private val NAMED_TAGS = setOf("param", "property")

/**
 * Parses a raw KDoc string — as returned by [com.google.devtools.ksp.symbol.KSDeclaration.docString] —
 * into a [ParsedKDoc]. Content is taken verbatim: no markdown, `[links]`, or inline tags are resolved.
 *
 * ```
 * val parsed = parseKDoc(
 *     "Configures the VAT rate.\n\n @property percentage the VAT percentage\n"
 * )
 * parsed.description                 // "Configures the VAT rate."
 * parsed.parameterDoc("percentage")  // "the VAT percentage"
 * ```
 */
internal fun parseKDoc(docString: String): ParsedKDoc {
    val descriptionLines = mutableListOf<String>()
    val blockTags = mutableListOf<BlockTag>()

    // The tag currently being accumulated, if any. A tag stays "open" across the lines that
    // follow it until the next tag line, so continuation lines append to its content.
    var openTag: String? = null
    var openName: String? = null
    val openContent = StringBuilder()

    fun commitOpenTag() {
        val tag = openTag ?: return
        blockTags += BlockTag(tag, openName, openContent.toString().trim())
        openContent.setLength(0)
    }

    for (line in docString.split("\n")) {
        val match = TAG_LINE.matchEntire(line)
        when {
            // A new block tag begins: close the previous one, then split off its name if named.
            match != null -> {
                commitOpenTag()
                val tag = match.groupValues[1]
                val (name, rest) = splitTagName(tag, match.groupValues[2].trim())
                openTag = tag
                openName = name
                openContent.append(rest)
            }
            // A continuation line for the open tag: join to its content with a single space.
            openTag != null -> openContent.appendContinuationLine(line)
            // Still before the first tag: this line is part of the prose description.
            // Trim per line to drop the leading space KSP leaves after stripping ` * `,
            // while keeping blank lines so paragraph breaks survive.
            else -> descriptionLines += line.trim()
        }
    }
    commitOpenTag()

    return ParsedKDoc(descriptionLines.joinToString("\n").trim(), blockTags)
}

/**
 * Splits the text following a tag keyword into the name of the documented element and the
 * remaining content. Only tags in [NAMED_TAGS] document a named element; for every other tag,
 * and for a named tag with no text at all, the whole [rest] is content and the name is `null`.
 */
private fun splitTagName(tag: String, rest: String): Pair<String?, String> {
    if (tag !in NAMED_TAGS || rest.isEmpty()) {
        return null to rest
    }
    val separator = rest.indexOfFirst { it.isWhitespace() }
    if (separator < 0) {
        return rest to ""
    }
    return rest.substring(0, separator) to rest.substring(separator + 1).trim()
}

/**
 * Appends a continuation line of the open tag, separated from what is already there by a single
 * space. Blank lines are dropped.
 */
private fun StringBuilder.appendContinuationLine(line: String) {
    if (line.isBlank()) {
        return
    }
    if (isNotEmpty()) {
        append(" ")
    }
    append(line.trim())
}

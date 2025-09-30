/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.annotation.processing.test.support

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

/**
 * Custom message collector for Kotlin compilation that collects messages into
 * [DiagnosticMessage] objects.
 */
internal class DiagnosticsMessageCollector(
    private val stepName: String,
    private val verbose: Boolean,
    private val diagnostics: MutableList<DiagnosticMessage>,
) : MessageCollector {

    override fun clear() {
        diagnostics.clear()
    }

    /**
     * Returns `true` if this collector has any warning messages.
     */
    fun hasWarnings() = diagnostics.any {
        it.severity == DiagnosticSeverity.WARNING
    }

    override fun hasErrors(): Boolean {
        return diagnostics.any {
            it.severity == DiagnosticSeverity.ERROR
        }
    }

    override fun report(
        severity: CompilerMessageSeverity,
        message: String,
        location: CompilerMessageSourceLocation?
    ) {
        if (!verbose && CompilerMessageSeverity.VERBOSE.contains(severity)) return

        val severity =
            if (stepName == "kapt" && getJavaVersion() >= 17) {
                // Workaround for KT-54030
                message.getSeverityFromPrefix() ?: severity.toSeverity()
            } else {
                severity.toSeverity()
            }
        doReport(severity, message)
    }

    private fun doReport(
        severity: DiagnosticSeverity,
        message: String,
    ) {
        if (message == KSP_ADDITIONAL_ERROR_MESSAGE) {
            // ignore this as it will impact error counts.
            return
        }
        // Strip kapt/ksp prefixes
        val strippedMessage = message.stripPrefixes()
        diagnostics.add(
            DiagnosticMessage(
                severity = severity,
                message = strippedMessage,
            )
        )
    }

    /**
     * Removes prefixes added by kapt / ksp from the message
     */
    private fun String.stripPrefixes(): String {
        return stripKind().stripKspPrefix()
    }

    /**
     * KAPT prepends the message kind to the message, we'll remove it here.
     */
    private fun String.stripKind(): String {
        val firstLine = lineSequence().firstOrNull() ?: return this
        val match = KIND_REGEX.find(firstLine) ?: return this
        return substring(match.range.last + 1)
    }

    /**
     * KSP prepends ksp to each message, we'll strip it here.
     */
    private fun String.stripKspPrefix(): String {
        val firstLine = lineSequence().firstOrNull() ?: return this
        val match = KSP_PREFIX_REGEX.find(firstLine) ?: return this
        return substring(match.range.last + 1)
    }

    private fun String.getSeverityFromPrefix(): DiagnosticSeverity? {
        val kindMatch =
            // The (\w+) for the kind prefix is is the 4th capture group
            KAPT_LOCATION_AND_KIND_REGEX.find(this)?.groupValues?.getOrNull(4)
            // The (\w+) is the 1st capture group
                ?: KIND_REGEX.find(this)?.groupValues?.getOrNull(1)
                ?: return null
        return if (kindMatch.equals("error", ignoreCase = true)) {
            DiagnosticSeverity.ERROR
        } else if (kindMatch.equals("warning", ignoreCase = true)) {
            DiagnosticSeverity.WARNING
        } else if (kindMatch.equals("note", ignoreCase = true)) {
            DiagnosticSeverity.INFO
        } else {
            null
        }
    }

    private fun getJavaVersion(): Int =
        System.getProperty("java.specification.version")?.substringAfter('.')?.toIntOrNull() ?: 6
    companion object {
        // example: foo/bar/Subject.kt:2: warning: the real message
        private val KAPT_LOCATION_AND_KIND_REGEX = """^(.*\.(kt|java)):(\d+): (\w+): """.toRegex()
        // detect things like "Note: " to be stripped from the message.
        // We could limit this to known diagnostic kinds (instead of matching \w:) but it is always
        // added so not really necessary until we hit a parser bug :)
        // example: "error: the real message"
        private val KIND_REGEX = """^(\w+): """.toRegex()
        // example: "[ksp] the real message"
        private val KSP_PREFIX_REGEX = """^\[ksp] """.toRegex()
        // KSP always prints an additional error if any other error occurred.
        // We drop that additional message to provide a more consistent error count with KAPT/javac.
        private const val KSP_ADDITIONAL_ERROR_MESSAGE =
            "Error occurred in KSP, check log for detail"
    }
}

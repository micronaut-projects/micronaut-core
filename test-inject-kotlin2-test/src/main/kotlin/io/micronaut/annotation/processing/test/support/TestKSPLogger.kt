/*
 * Copyright 2017-2018 original authors
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

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.NonExistLocation
import java.io.PrintWriter
import java.io.StringWriter
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

internal class TestKSPLogger(
  private val messageCollector: MessageCollector,
  private val allWarningsAsErrors: Boolean,
) : KSPLogger {

  companion object {
    const val PREFIX = "[ksp] "
  }

  data class Event(val severity: CompilerMessageSeverity, val message: String)

  val recordedEvents = mutableListOf<Event>()

  private val reportToCompilerSeverity =
    setOf(CompilerMessageSeverity.ERROR, CompilerMessageSeverity.EXCEPTION)

  private var reportedToCompiler = false

  private fun convertMessage(message: String, symbol: KSNode?): String =
    when (val location = symbol?.location) {
      is FileLocation -> "$PREFIX${location.filePath}:${location.lineNumber}: $message"
      is NonExistLocation,
      null -> "$PREFIX$message"
    }

  override fun logging(message: String, symbol: KSNode?) {
    recordedEvents.add(Event(CompilerMessageSeverity.LOGGING, convertMessage(message, symbol)))
  }

  override fun info(message: String, symbol: KSNode?) {
    recordedEvents.add(Event(CompilerMessageSeverity.INFO, convertMessage(message, symbol)))
  }

  override fun warn(message: String, symbol: KSNode?) {
    val severity =
      if (allWarningsAsErrors) CompilerMessageSeverity.ERROR else CompilerMessageSeverity.WARNING
    recordedEvents.add(Event(severity, convertMessage(message, symbol)))
  }

  override fun error(message: String, symbol: KSNode?) {
    recordedEvents.add(Event(CompilerMessageSeverity.ERROR, convertMessage(message, symbol)))
  }

  override fun exception(e: Throwable) {
    val writer = StringWriter()
    e.printStackTrace(PrintWriter(writer))
    recordedEvents.add(Event(CompilerMessageSeverity.EXCEPTION, writer.toString()))
  }

  fun reportAll() {
    for (event in recordedEvents) {
      if (!reportedToCompiler && event.severity in reportToCompilerSeverity) {
        reportedToCompiler = true
        messageCollector.report(event.severity, "Error occurred in KSP, check log for detail")
      }
      messageCollector.report(event.severity, event.message)
    }
  }
}

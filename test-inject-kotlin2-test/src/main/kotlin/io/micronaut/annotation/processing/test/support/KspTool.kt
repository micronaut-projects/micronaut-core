/*
 * Copyright 2017-2023 original authors
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

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

sealed interface KspTool {
  val symbolProcessorProviders: MutableList<SymbolProcessorProvider>
  val processorOptions: MutableMap<String, String>
  var incremental: Boolean
  var incrementalLog: Boolean
  var allWarningsAsErrors: Boolean
  var withCompilation: Boolean
  var loggingLevels: Set<CompilerMessageSeverity>
}

/** Gets or creates the [KspTool] if it doesn't exist. */
@OptIn(ExperimentalCompilerApi::class)
internal fun KotlinCompilation.getKspTool(): KspTool {
  val ksp2Tool = precursorTools["ksp2"] as? Ksp2PrecursorTool?
  return ksp2Tool ?: getKspRegistrar()
}

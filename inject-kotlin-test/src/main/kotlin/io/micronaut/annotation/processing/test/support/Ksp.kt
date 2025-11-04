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

import com.google.devtools.ksp.AbstractKotlinSymbolProcessingExtension
import com.google.devtools.ksp.KspOptions
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.processing.impl.MessageCollectorBasedKSPLogger
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.jvm.config.JavaSourceRoot
import org.jetbrains.kotlin.com.intellij.core.CoreApplicationEnvironment
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.psi.PsiTreeChangeAdapter
import org.jetbrains.kotlin.com.intellij.psi.PsiTreeChangeListener
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import java.io.File
import java.util.EnumSet

/** Configure the given KSP tool for this compilation. */
@OptIn(ExperimentalCompilerApi::class)
fun KotlinCompilation.configureKsp(useKsp2: Boolean = false, body: KspTool.() -> Unit) {
    if (useKsp2) {
        useKsp2()
    }
    getKspTool().body()
}

/** The list of symbol processors for the kotlin compilation. https://goo.gle/ksp */
@OptIn(ExperimentalCompilerApi::class)
var KotlinCompilation.symbolProcessorProviders: MutableList<SymbolProcessorProvider>
    get() = getKspTool().symbolProcessorProviders
    set(value) {
        val tool = getKspTool()
        tool.symbolProcessorProviders.clear()
        tool.symbolProcessorProviders.addAll(value)
    }

/** The directory where generated KSP sources are written */
@OptIn(ExperimentalCompilerApi::class)
val KotlinCompilation.kspSourcesDir: File
    get() = kspWorkingDir.resolve("sources")

/** Arbitrary arguments to be passed to ksp */
@OptIn(ExperimentalCompilerApi::class)
@Deprecated(
    "Use kspProcessorOptions",
    replaceWith =
        ReplaceWith("kspProcessorOptions", "com.tschuchort.compiletesting.kspProcessorOptions"),
)
var KotlinCompilation.kspArgs: MutableMap<String, String>
    get() = kspProcessorOptions
    set(options) {
        kspProcessorOptions = options
    }

/** Arbitrary processor options to be passed to ksp */
@OptIn(ExperimentalCompilerApi::class)
var KotlinCompilation.kspProcessorOptions: MutableMap<String, String>
    get() = getKspTool().processorOptions
    set(options) {
        val tool = getKspTool()
        tool.processorOptions.clear()
        tool.processorOptions.putAll(options)
    }

/** Controls for enabling incremental processing in KSP. */
@OptIn(ExperimentalCompilerApi::class)
var KotlinCompilation.kspIncremental: Boolean
    get() = getKspTool().incremental
    set(value) {
        val tool = getKspTool()
        tool.incremental = value
    }

/** Controls for enabling incremental processing logs in KSP. */
@OptIn(ExperimentalCompilerApi::class)
var KotlinCompilation.kspIncrementalLog: Boolean
    get() = getKspTool().incrementalLog
    set(value) {
        val tool = getKspTool()
        tool.incrementalLog = value
    }

/** Controls for enabling all warnings as errors in KSP. */
@OptIn(ExperimentalCompilerApi::class)
var KotlinCompilation.kspAllWarningsAsErrors: Boolean
    get() = getKspTool().allWarningsAsErrors
    set(value) {
        val tool = getKspTool()
        tool.allWarningsAsErrors = value
    }

/**
 * Run processors and compilation in a single compiler invocation if true. See
 * [com.google.devtools.ksp.KspCliOption.WITH_COMPILATION_OPTION].
 */
@OptIn(ExperimentalCompilerApi::class)
var KotlinCompilation.kspWithCompilation: Boolean
    get() = getKspTool().withCompilation
    set(value) {
        val tool = getKspTool()
        tool.withCompilation = value
    }

/** Sets logging levels for KSP. Default is all. */
@OptIn(ExperimentalCompilerApi::class)
var KotlinCompilation.kspLoggingLevels: Set<CompilerMessageSeverity>
    get() = getKspTool().loggingLevels
    set(value) {
        val tool = getKspTool()
        tool.loggingLevels = value
    }

@ExperimentalCompilerApi
val JvmCompilationResult.sourcesGeneratedBySymbolProcessor: Sequence<File>
    get() = outputDirectory.parentFile.resolve("ksp/sources")
        .walkTopDown()
        .filter { it.isFile }

@OptIn(ExperimentalCompilerApi::class)
internal val KotlinCompilation.kspJavaSourceDir: File
    get() = kspSourcesDir.resolve("java")

@OptIn(ExperimentalCompilerApi::class)
internal val KotlinCompilation.kspKotlinSourceDir: File
    get() = kspSourcesDir.resolve("kotlin")

@OptIn(ExperimentalCompilerApi::class)
internal val KotlinCompilation.kspResources: File
    get() = kspSourcesDir.resolve("resources")

/** The working directory for KSP */
@OptIn(ExperimentalCompilerApi::class)
internal val KotlinCompilation.kspWorkingDir: File
    get() = workingDir.resolve("ksp")

/** The directory where compiled KSP classes are written */
// TODO this seems to be ignored by KSP and it is putting classes into regular classes directory
//  but we still need to provide it in the KSP options builder as it is required
//  once it works, we should make the property public.
@OptIn(ExperimentalCompilerApi::class)
internal val KotlinCompilation.kspClassesDir: File
    get() = kspWorkingDir.resolve("classes")

/** The directory where compiled KSP caches are written */
@OptIn(ExperimentalCompilerApi::class)
internal val KotlinCompilation.kspCachesDir: File
    get() = kspWorkingDir.resolve("caches")

/**
 * Custom subclass of [AbstractKotlinSymbolProcessingExtension] where processors are pre-defined
 * instead of being loaded via ServiceLocator.
 */
private class KspTestExtension(
    options: KspOptions,
    processorProviders: List<SymbolProcessorProvider>,
    logger: KSPLogger,
) : AbstractKotlinSymbolProcessingExtension(options = options, logger = logger, testMode = false) {
    private val loadedProviders = processorProviders

    override fun loadProviders(rootDisposable: Disposable): List<SymbolProcessorProvider> = loadedProviders
}

/** Registers the [KspTestExtension] to load the given list of processors. */
@OptIn(ExperimentalCompilerApi::class)
internal class KspCompileTestingComponentRegistrar(private val compilation: KotlinCompilation) :
    ComponentRegistrar, KspTool {
    override var symbolProcessorProviders = mutableListOf<SymbolProcessorProvider>()
    override var processorOptions = mutableMapOf<String, String>()
    override var incremental: Boolean = false
    override var incrementalLog: Boolean = false
    override var allWarningsAsErrors: Boolean = false
    override var withCompilation: Boolean = false
    override var loggingLevels: Set<CompilerMessageSeverity> =
        EnumSet.allOf(CompilerMessageSeverity::class.java)

    override fun registerProjectComponents(
        project: MockProject,
        configuration: CompilerConfiguration,
    ) {
        if (symbolProcessorProviders.isEmpty()) {
            return
        }
        val options =
            KspOptions.Builder()
                .apply {
                    this.projectBaseDir = compilation.kspWorkingDir

                    this.processingOptions.putAll(compilation.kspArgs)

                    this.incremental = this@KspCompileTestingComponentRegistrar.incremental
                    this.incrementalLog = this@KspCompileTestingComponentRegistrar.incrementalLog
                    this.allWarningsAsErrors = this@KspCompileTestingComponentRegistrar.allWarningsAsErrors
                    this.withCompilation = this@KspCompileTestingComponentRegistrar.withCompilation

                    this.cachesDir =
                        compilation.kspCachesDir.also {
                            it.deleteRecursively()
                            it.mkdirs()
                        }
                    this.kspOutputDir =
                        compilation.kspSourcesDir.also {
                            it.deleteRecursively()
                            it.mkdirs()
                        }
                    this.classOutputDir =
                        compilation.kspClassesDir.also {
                            it.deleteRecursively()
                            it.mkdirs()
                        }
                    this.javaOutputDir =
                        compilation.kspJavaSourceDir.also {
                            it.deleteRecursively()
                            it.mkdirs()
                            compilation.registerGeneratedSourcesDir(it)
                        }
                    this.kotlinOutputDir =
                        compilation.kspKotlinSourceDir.also {
                            it.deleteRecursively()
                            it.mkdirs()
                        }
                    this.resourceOutputDir =
                        compilation.kspResources.also {
                            it.deleteRecursively()
                            it.mkdirs()
                        }
                    this.languageVersionSettings = configuration.languageVersionSettings
                    configuration[CLIConfigurationKeys.CONTENT_ROOTS]
                        ?.filterIsInstance<JavaSourceRoot>()
                        ?.forEach { this.javaSourceRoots.add(it.file) }
                }
                .build()

        // Temporary until friend-paths is fully supported https://youtrack.jetbrains.com/issue/KT-34102
        @Suppress("invisible_member", "invisible_reference")
        val messageCollector = compilation.createMessageCollectorAccess("ksp")
        val messageCollectorBasedKSPLogger =
            MessageCollectorBasedKSPLogger(
                messageCollector = messageCollector,
                wrappedMessageCollector = messageCollector,
                allWarningsAsErrors = allWarningsAsErrors,
            )
        val registrar =
            KspTestExtension(options, symbolProcessorProviders, messageCollectorBasedKSPLogger)
        AnalysisHandlerExtension.registerExtension(project, registrar)
        // Dummy extension point; Required by dropPsiCaches().
        CoreApplicationEnvironment.registerExtensionPoint(
            project.extensionArea,
            PsiTreeChangeListener.EP.name,
            PsiTreeChangeAdapter::class.java,
        )
    }
}

/** Gets the test registrar from the plugin list or adds if it does not exist. */
@OptIn(ExperimentalCompilerApi::class)
internal fun KotlinCompilation.getKspRegistrar(): KspCompileTestingComponentRegistrar {
    componentRegistrars.firstIsInstanceOrNull<KspCompileTestingComponentRegistrar>()?.let {
        return it
    }
    val kspRegistrar = KspCompileTestingComponentRegistrar(this)
    componentRegistrars += kspRegistrar
    return kspRegistrar
}

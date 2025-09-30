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

import java.io.File
import java.io.OutputStreamWriter
import java.nio.file.Path
import javax.annotation.processing.Processor
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.JVMAssertionsMode
import org.jetbrains.kotlin.config.JvmDefaultMode
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.Services

data class PluginOption(
    val pluginId: PluginId,
    val optionName: OptionName,
    val optionValue: OptionValue,
)

typealias PluginId = String

typealias OptionName = String

typealias OptionValue = String

@ExperimentalCompilerApi
@Suppress("MemberVisibilityCanBePrivate")
class KotlinCompilation : AbstractKotlinCompilation<K2JVMCompilerArguments>() {
    /** Arbitrary arguments to be passed to kapt */
    var kaptArgs: MutableMap<OptionName, OptionValue> = mutableMapOf()

    /** Enables the new Kapt 4 impl for K2 support. */
    var useKapt4: Boolean? = null

    /** Annotation processors to be passed to kapt */
    var annotationProcessors: List<Processor> = emptyList()

    /** Include Kotlin runtime in to resulting .jar */
    var includeRuntime: Boolean = false

    /** Make kapt correct error types */
    var correctErrorTypes: Boolean = true

    /** Name of the generated .kotlin_module file */
    var moduleName: String? = null

    /** Target version of the generated JVM bytecode */
    var jvmTarget: String = JvmTarget.DEFAULT.description

    /** Generate metadata for Java 1.8 reflection on method parameters */
    var javaParameters: Boolean = false

    /** Use the old JVM backend */
    var useOldBackend: Boolean = false

    /** Paths where to find Java 9+ modules */
    var javaModulePath: Path? = null

    /**
     * Root modules to resolve in addition to the initial modules, or all modules on the module path
     * if <module> is ALL-MODULE-PATH
     */
    var additionalJavaModules: MutableList<File> = mutableListOf()

    /** Don't generate not-null assertions for arguments of platform types */
    var noCallAssertions: Boolean = false

    /** Don't generate not-null assertion for extension receiver arguments of platform types */
    var noReceiverAssertions: Boolean = false

    /** Don't generate not-null assertions on parameters of methods accessible from Java */
    var noParamAssertions: Boolean = false

    /** Generate nullability assertions for non-null Java expressions */
    @Deprecated("Removed in Kotlinc, this does nothing now.")
    var strictJavaNullabilityAssertions: Boolean? = null

    /** Disable optimizations */
    var noOptimize: Boolean = false

    /**
     * Normalize constructor calls (disable: don't normalize; enable: normalize), default is 'disable'
     * in language version 1.2 and below, 'enable' since language version 1.3
     *
     * {disable|enable}
     */
    @Deprecated("Removed in Kotlinc, this does nothing now.")
    var constructorCallNormalizationMode: String? = null

    /** Assert calls behaviour {always-enable|always-disable|jvm|legacy} */
    var assertionsMode: String? = JVMAssertionsMode.DEFAULT.description

    /** Path to the .xml build file to compile */
    var buildFile: File? = null

    /** Compile multifile classes as a hierarchy of parts and facade */
    var inheritMultifileParts: Boolean = false

    /** Use type table in metadata serialization */
    var useTypeTable: Boolean = false

    /** Allow Kotlin runtime libraries of incompatible versions in the classpath */
    @Deprecated("Removed in Kotlinc, this does nothing now.")
    var skipRuntimeVersionCheck: Boolean? = null

    /** Combine modules for source files and binary dependencies into a single module */
    @Deprecated("Removed in Kotlinc, this does nothing now.") var singleModule: Boolean = false

    /** Suppress the \"cannot access built-in declaration\" error (useful with -no-stdlib) */
    var suppressMissingBuiltinsError: Boolean = false

    /** Script resolver environment in key-value pairs (the value could be quoted and escaped) */
    var scriptResolverEnvironment: MutableMap<String, String> = mutableMapOf()

    /** Java compiler arguments */
    var javacArguments: MutableList<String> = mutableListOf()

    /** Package prefix for Java files */
    var javaPackagePrefix: String? = null

    /**
     * Specify behavior for Checker Framework compatqual annotations (NullableDecl/NonNullDecl).
     * Default value is 'enable'
     */
    var supportCompatqualCheckerFrameworkAnnotations: String? = null

    /**
     * Do not throw NPE on explicit 'equals' call for null receiver of platform boxed primitive type
     */
    @Deprecated("Removed in Kotlinc, this does nothing now.")
    var noExceptionOnExplicitEqualsForBoxedNull: Boolean? = null

    /**
     * Allow to use '@JvmDefault' annotation for JVM default method support.
     * {disable|enable|compatibility}
     */
    var jvmDefault: String = JvmDefaultMode.DISABLE.description

    /** Generate metadata with strict version semantics (see kdoc on Metadata.extraInt) */
    var strictMetadataVersionSemantics: Boolean = false

    /**
     * Transform '(' and ')' in method names to some other character sequence. This mode can BREAK
     * BINARY COMPATIBILITY and is only supposed to be used as a workaround of an issue in the ASM
     * bytecode framework. See KT-29475 for more details
     */
    var sanitizeParentheses: Boolean = false

    /** Paths to output directories for friend modules (whose internals should be visible) */
    var friendPaths: List<File> = emptyList()

    /**
     * Path to the JDK to be used
     *
     * If null, no JDK will be used with kotlinc (option -no-jdk) and the system java compiler will be
     * used with empty bootclasspath (on JDK8) or --system none (on JDK9+). This can be useful if all
     * the JDK classes you need are already on the (inherited) classpath.
     */
    var jdkHome: File? by default { processJdkHome }

    /**
     * Path to the kotlin-stdlib.jar If none is given, it will be searched for in the host process'
     * classpaths
     */
    var kotlinStdLibJar: File? by default { HostEnvironment.kotlinStdLibJar }

    /**
     * Path to the kotlin-stdlib-jdk*.jar If none is given, it will be searched for in the host
     * process' classpaths
     */
    var kotlinStdLibJdkJar: File? by default { HostEnvironment.kotlinStdLibJdkJar }

    /**
     * Path to the kotlin-reflect.jar If none is given, it will be searched for in the host process'
     * classpaths
     */
    var kotlinReflectJar: File? by default { HostEnvironment.kotlinReflectJar }

    /**
     * Path to the kotlin-script-runtime.jar If none is given, it will be searched for in the host
     * process' classpaths
     */
    var kotlinScriptRuntimeJar: File? by default { HostEnvironment.kotlinScriptRuntimeJar }

    /**
     * Path to the tools.jar file needed for kapt when using a JDK 8.
     *
     * Note: Using a tools.jar file with a JDK 9 or later leads to an internal compiler error!
     */
    var toolsJar: File? by default {
        if (!isJdk9OrLater()) jdkHome?.let { findToolsJarFromJdk(it) } ?: HostEnvironment.toolsJar
        else null
    }

    // *.class files, Jars and resources (non-temporary) that are created by the
    // compilation will land here
    val classesDir
        get() = workingDir.resolve("classes")

    // Base directory for kapt stuff
    private val kaptBaseDir
        get() = workingDir.resolve("kapt")

    // Java annotation processors that are compile by kapt will put their generated files here
    val kaptSourceDir
        get() = kaptBaseDir.resolve("sources")

    // Output directory for Kotlin source files generated by kapt
    val kaptKotlinGeneratedDir
        get() =
            kaptArgs[OPTION_KAPT_KOTLIN_GENERATED]?.let { path ->
                require(File(path).isDirectory) { "$OPTION_KAPT_KOTLIN_GENERATED must be a directory" }
                File(path)
            } ?: File(kaptBaseDir, "kotlinGenerated")

    val kaptStubsDir
        get() = kaptBaseDir.resolve("stubs")

    val kaptIncrementalDataDir
        get() = kaptBaseDir.resolve("incrementalData")

    /** ExitCode of the entire Kotlin compilation process */
    enum class ExitCode {
        OK,
        INTERNAL_ERROR,
        COMPILATION_ERROR,
        SCRIPT_EXECUTION_ERROR
    }

    private fun useKapt4(): Boolean {
        return (useKapt4 ?: languageVersion?.startsWith("2")) == true
    }

    // setup common arguments for the two kotlinc calls
    private fun commonK2JVMArgs() =
        commonArguments(K2JVMCompilerArguments()) { args ->
            args.destination = classesDir.absolutePath
            args.classpath = commonClasspaths().joinToString(separator = File.pathSeparator)

            if (jdkHome != null) {
                args.jdkHome = jdkHome!!.absolutePath
            } else {
                log("Using option -no-jdk. Kotlinc won't look for a JDK.")
                args.noJdk = true
            }

            args.includeRuntime = includeRuntime

            // the compiler should never look for stdlib or reflect in the
            // kotlinHome directory (which is null anyway). We will put them
            // in the classpath manually if they're needed
            args.noStdlib = true
            args.noReflect = true

            if (moduleName != null) args.moduleName = moduleName

            args.jvmTarget = jvmTarget
            args.javaParameters = javaParameters
            args.useOldBackend = useOldBackend

            if (javaModulePath != null) args.javaModulePath = javaModulePath!!.toString()

            args.additionalJavaModules = additionalJavaModules.map(File::getAbsolutePath).toTypedArray()
            args.noCallAssertions = noCallAssertions
            args.noParamAssertions = noParamAssertions
            args.noReceiverAssertions = noReceiverAssertions

            args.noOptimize = noOptimize

            if (assertionsMode != null) args.assertionsMode = assertionsMode

            if (buildFile != null) args.buildFile = buildFile!!.toString()

            args.inheritMultifileParts = inheritMultifileParts
            args.useTypeTable = useTypeTable

            if (javacArguments.isNotEmpty()) args.javacArguments = javacArguments.toTypedArray()

            if (supportCompatqualCheckerFrameworkAnnotations != null)
                args.supportCompatqualCheckerFrameworkAnnotations =
                    supportCompatqualCheckerFrameworkAnnotations

            args.jvmDefault = jvmDefault
            args.strictMetadataVersionSemantics = strictMetadataVersionSemantics
            args.sanitizeParentheses = sanitizeParentheses

            if (friendPaths.isNotEmpty())
                args.friendPaths = friendPaths.map(File::getAbsolutePath).toTypedArray()

            if (scriptResolverEnvironment.isNotEmpty())
                args.scriptResolverEnvironment =
                    scriptResolverEnvironment.map { (key, value) -> "$key=\"$value\"" }.toTypedArray()

            args.javaPackagePrefix = javaPackagePrefix
            args.suppressMissingBuiltinsError = suppressMissingBuiltinsError
            args.disableStandardScript = disableStandardScript
        }

    /** Performs the 1st and 2nd compilation step to generate stubs and run annotation processors */
    private fun stubsAndApt(sourceFiles: List<File>): ExitCode {
        if (annotationProcessors.isEmpty()) {
            log("No services were given. Not running kapt steps.")
            return ExitCode.OK
        }

        val compilerMessageCollector = createMessageCollector("kapt")

        /*
         * The main compiler plugin (MainComponentRegistrar)
         * is instantiated by K2JVMCompiler using
         * a service locator. So we can't just pass parameters to it easily.
         * Instead, we need to use a thread-local global variable to pass
         * any parameters that change between compilations
         */
        MainComponentRegistrar.threadLocalParameters.set(
            MainComponentRegistrar.ThreadLocalParameters(
                componentRegistrars,
                compilerPluginRegistrars,
                supportsK2,
            )
        )

        val kotlinSources = sourceFiles.filter(File::hasKotlinFileExtension)
        val javaSources = sourceFiles.filter(File::hasJavaFileExtension)

        val sourcePaths = javaSources.plus(kotlinSources).map(File::getAbsolutePath).distinct()

        if (pluginClasspaths.isNotEmpty()) {
            warn("Included plugins in pluginsClasspaths will be executed twice.")
        }

        val k2JvmArgs =
            commonK2JVMArgs().also {
                it.freeArgs = sourcePaths
                it.pluginClasspaths = (it.pluginClasspaths ?: emptyArray()) + arrayOf(getResourcesPath())
                if (kotlinSources.isEmpty()) {
                    it.allowNoSourceFiles = true
                }
            }

        return convertKotlinExitCode(
            K2JVMCompiler().exec(compilerMessageCollector, Services.EMPTY, k2JvmArgs)
        )
    }

    /** Performs the 3rd compilation step to compile Kotlin source files */
    private fun compileJvmKotlin(sourceFiles: List<File>): ExitCode {
        val sources =
            sourceFiles +
                    kaptKotlinGeneratedDir.listFilesRecursively() +
                    kaptSourceDir.listFilesRecursively()

        return compileKotlin(sources, K2JVMCompiler(), commonK2JVMArgs())
    }

    /**
     * Base javac arguments that only depend on the arguments given by the user Depending on which
     * compiler implementation is actually used, more arguments may be added
     */
    private fun baseJavacArgs(isJavac9OrLater: Boolean) =
        mutableListOf<String>().apply {
            if (verbose) {
                add("-verbose")
                add("-Xlint:path") // warn about invalid paths in CLI
                add("-Xlint:options") // warn about invalid options in CLI

                if (isJavac9OrLater) add("-Xlint:module") // warn about issues with the module system
            }

            addAll("-d", classesDir.absolutePath)

            add("-proc:none") // disable annotation processing

            if (allWarningsAsErrors) add("-Werror")

            addAll(javacArguments)

            // also add class output path to javac classpath so it can discover
            // already compiled Kotlin classes
            addAll(
                "-cp",
                (commonClasspaths() + classesDir).joinToString(
                    File.pathSeparator,
                    transform = File::getAbsolutePath,
                ),
            )
        }

    /** Performs the 4th compilation step to compile Java source files */
    private fun compileJava(sourceFiles: List<File>): ExitCode {
        val javaSources =
            sourceFiles
                .plus(kaptSourceDir.listFilesRecursively())
                .plus(
                    extraGeneratedSources
                        .flatMap(File::listFilesRecursively)
                        .filter(File::hasJavaFileExtension)
                )
                .distinct()
                .filterNot(File::hasKotlinFileExtension)

        if (javaSources.isEmpty()) return ExitCode.OK

        if (jdkHome != null && jdkHome!!.canonicalPath != processJdkHome.canonicalPath) {
            /* If a JDK home is given, try to run javac from there so it uses the same JDK
            as K2JVMCompiler. Changing the JDK of the system java compiler via the
            "--system" and "-bootclasspath" options is not so easy.
            If the jdkHome is the same as the current process, we still run an in process compilation because it is
            expensive to fork a process to compile.
            */
            log("compiling java in a sub-process because a jdkHome is specified")
            val jdkBinFile = File(jdkHome, "bin")
            check(jdkBinFile.exists()) { "No JDK bin folder found at: ${jdkBinFile.toPath()}" }

            val javacCommand = jdkBinFile.absolutePath + File.separatorChar + "javac"

            val isJavac9OrLater = isJavac9OrLater(getJavacVersionString(javacCommand))
            val javacArgs = baseJavacArgs(isJavac9OrLater)

            val javacProc =
                ProcessBuilder(listOf(javacCommand) + javacArgs + javaSources.map(File::getAbsolutePath))
                    .directory(workingDir)
                    .redirectErrorStream(true)
                    .start()

            javacProc.inputStream.copyTo(internalMessageStream)
            javacProc.errorStream.copyTo(internalMessageStream)

            return when (javacProc.waitFor()) {
                0 -> ExitCode.OK
                1 -> ExitCode.COMPILATION_ERROR
                else -> ExitCode.INTERNAL_ERROR
            }
        } else {
            /*  If no JDK is given, we will use the host process' system java compiler.
            If it is set to `null`, we will erase the bootclasspath. The user is then on their own to somehow
            provide the JDK classes via the regular classpath because javac won't
            work at all without them */
            log("jdkHome is not specified. Using system java compiler of the host process.")
            val isJavac9OrLater = isJdk9OrLater()
            val javacArgs =
                baseJavacArgs(isJavac9OrLater).apply {
                    if (jdkHome == null) {
                        log("jdkHome is set to null, removing boot classpath from java compilation")
                        // erase bootclasspath or JDK path because no JDK was specified
                        if (isJavac9OrLater) addAll("--system", "none") else addAll("-bootclasspath", "")
                    }
                }

            val javac = SynchronizedToolProvider.systemJavaCompiler
            val javaFileManager = javac.getStandardFileManager(null, null, null)
            val diagnosticCollector = DiagnosticCollector<JavaFileObject>()

            fun printDiagnostics() =
                diagnosticCollector.diagnostics.forEach { diag ->
                    // Print toString() for these to get the full error message
                    when (diag.kind) {
                        Diagnostic.Kind.ERROR -> error(diag.toString())
                        Diagnostic.Kind.WARNING,
                        Diagnostic.Kind.MANDATORY_WARNING -> warn(diag.toString())
                        else -> log(diag.toString())
                    }
                }

            try {
                val noErrors =
                    javac
                        .getTask(
                            OutputStreamWriter(internalMessageStream),
                            javaFileManager,
                            diagnosticCollector,
                            javacArgs,
                            /* classes to be annotation processed */ null,
                            javaSources
                                .map { FileJavaFileObject(it) }
                                .filter { it.kind == JavaFileObject.Kind.SOURCE },
                        )
                        .call()

                printDiagnostics()

                return if (noErrors) ExitCode.OK else ExitCode.COMPILATION_ERROR
            } catch (e: Exception) {
                if (e is RuntimeException) {
                    printDiagnostics()
                    error(e.toString())
                    return ExitCode.INTERNAL_ERROR
                } else throw e
            }
        }
    }

    /** Runs the compilation task */
    fun compile(): JvmCompilationResult {
        // make sure all needed directories exist
        sourcesDir.deleteRecursively()
        sourcesDir.mkdirs()
        classesDir.mkdirs()
        kaptSourceDir.mkdirs()
        kaptStubsDir.mkdirs()
        kaptIncrementalDataDir.mkdirs()
        kaptKotlinGeneratedDir.mkdirs()

        // write given sources to working directory
        val sourceFiles = sources.map { it.writeTo(sourcesDir) }

        pluginClasspaths.forEach { filepath ->
            if (!filepath.exists()) {
                error("Plugin $filepath not found")
                return makeResult(ExitCode.INTERNAL_ERROR)
            }
        }

        /*
        There are 4 steps to the compilation process:
        1. Generate stubs (using kotlinc with kapt plugin which does no further compilation)
        2. Run apt (using kotlinc with kapt plugin which does no further compilation)
        3. Run kotlinc with the normal Kotlin sources and Kotlin sources generated in step 2
        4. Run javac with Java sources and the compiled Kotlin classes
         */

        /* Work around for warning that sometimes happens:
        "Failed to initialize native filesystem for Windows
        java.lang.RuntimeException: Could not find installation home path.
        Please make sure bin/idea.properties is present in the installation directory"
        See: https://github.com/arturbosch/detekt/issues/630
        */
        withSystemProperty("idea.use.native.fs.for.win", "false") {
            // step 1 and 2: generate stubs and run annotation processors
            try {
                val exitCode = stubsAndApt(sourceFiles)
                if (exitCode != ExitCode.OK) {
                    return makeResult(exitCode)
                }
            } finally {
                MainComponentRegistrar.threadLocalParameters.remove()
            }

            // step 3: compile Kotlin files
            compileJvmKotlin(sourceFiles).let { exitCode ->
                if (exitCode != ExitCode.OK) {
                    return makeResult(exitCode)
                }
            }
        }

        // step 4: compile Java files
        return makeResult(compileJava(sourceFiles))
    }

    private fun makeResult(exitCode: ExitCode): JvmCompilationResult {
        val messages = internalMessageBuffer.readUtf8()

        if (exitCode != ExitCode.OK) searchSystemOutForKnownErrors(messages)

        return JvmCompilationResult(exitCode, messages, diagnostics, this)
    }

    internal fun commonClasspaths() =
        mutableListOf<File>()
            .apply {
                addAll(classpaths)
                addAll(
                    listOfNotNull(
                        kotlinStdLibJar,
                        kotlinStdLibCommonJar,
                        kotlinStdLibJdkJar,
                        kotlinReflectJar,
                        kotlinScriptRuntimeJar,
                    )
                )

                if (inheritClassPath) {
                    addAll(hostClasspaths)
                    log("Inheriting classpaths:  " + hostClasspaths.joinToString(File.pathSeparator))
                }
            }
            .distinct()

    companion object {
        const val OPTION_KAPT_KOTLIN_GENERATED = "kapt.kotlin.generated"
    }
}

/**
 * Adds the output directory of [previousResult] to the classpath of this compilation. This is a
 * convenience for
 *
 * ```
 * this.classpaths += previousResult.outputDirectory
 * ```
 */
@ExperimentalCompilerApi
fun KotlinCompilation.addPreviousResultToClasspath(
    previousResult: JvmCompilationResult
): KotlinCompilation = apply { classpaths += previousResult.outputDirectory }

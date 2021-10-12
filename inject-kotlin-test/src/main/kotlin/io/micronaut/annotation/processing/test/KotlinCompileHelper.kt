/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.annotation.processing.test

import org.jetbrains.kotlin.base.kapt3.DetectMemoryLeaksMode
import org.jetbrains.kotlin.base.kapt3.KaptOptions
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.codegen.ClassBuilderMode
import org.jetbrains.kotlin.codegen.DefaultCodegenFactory
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.OriginCollectingClassBuilderFactory
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileFactoryImpl
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.kapt3.AbstractKapt3Extension
import org.jetbrains.kotlin.kapt3.base.LoadedProcessors
import org.jetbrains.kotlin.kapt3.base.incremental.DeclaredProcType
import org.jetbrains.kotlin.kapt3.base.incremental.IncrementalProcessor
import org.jetbrains.kotlin.kapt3.util.MessageCollectorBackedKaptLogger
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.AnalyzingUtils
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import javax.annotation.processing.Processor

object KotlinCompileHelper {
    init {
        System.setProperty("idea.ignore.disabled.plugins", "true")
        System.setProperty("idea.io.use.nio2", "true")
    }

    fun run(className: String, code: String): Result {
        val tmp = Files.createTempDirectory("KotlinCompileHelper")
        try {
            return run0(tmp, className, code)
        } finally {
            // delete tmp dir
            Files.walkFileTree(tmp, object : FileVisitor<Path> {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    throw exc
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            })
        }
    }

    private fun run0(
        tmp: Path,
        className: String,
        code: String
    ): Result {
        val outDir = tmp.resolve("out")
        Files.createDirectory(outDir)
        val stubsDir = tmp.resolve("stubs")
        Files.createDirectory(stubsDir)

        val configuration = CompilerConfiguration()
        configuration.put(CommonConfigurationKeys.MODULE_NAME, "test-module")
        val messageCollector = object : MessageCollector {
            override fun clear() {
            }

            override fun hasErrors() = false

            override fun report(
                severity: CompilerMessageSeverity,
                message: String,
                location: CompilerMessageSourceLocation?
            ) {
                if (severity == CompilerMessageSeverity.ERROR) {
                    throw AssertionError("Error reported in processing: $message")
                }
            }
        }
        configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
        configuration.put(JVMConfigurationKeys.IR, false)
        configuration.put(JVMConfigurationKeys.OUTPUT_DIRECTORY, outDir.toFile())

        val env =
            KotlinCoreEnvironment.createForTests({ }, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)

        val cp = getClasspath(KotlinCompileHelper::class.java.classLoader) + getClasspathFromSystemProperty("java.class.path") + getClasspathFromSystemProperty("sun.boot.class.path")
        env.updateClasspath(cp.map {
            JvmClasspathRoot(it, false)
        })

        val kaptOptions = KaptOptions.Builder()
        kaptOptions.projectBaseDir = tmp.toFile()
        kaptOptions.sourcesOutputDir = outDir.toFile()
        kaptOptions.classesOutputDir = outDir.toFile()
        kaptOptions.stubsOutputDir = stubsDir.toFile()
        kaptOptions.detectMemoryLeaks = DetectMemoryLeaksMode.NONE
        kaptOptions.compileClasspath.addAll(cp)

        class KaptExtension : AbstractKapt3Extension(
            kaptOptions.build(),
            MessageCollectorBackedKaptLogger(
                isVerbose = false,
                isInfoAsWarnings = true,
                messageCollector = messageCollector
            ),
            configuration
        ) {
            override fun loadProcessors() = LoadedProcessors(
                ServiceLoader.load(Processor::class.java)
                    .map { IncrementalProcessor(it, DeclaredProcType.NON_INCREMENTAL, logger) },
                KotlinCompileHelper::class.java.classLoader
            )
        }

        AnalysisHandlerExtension.registerExtension(env.project, KaptExtension())

        val classBuilderFactory = OriginCollectingClassBuilderFactory(ClassBuilderMode.FULL)
        val vFile =
            LightVirtualFile(className.substring(className.lastIndexOf('.') + 1) + ".kt", KotlinLanguage.INSTANCE, code)
        vFile.charset = StandardCharsets.UTF_8
        val psiFileFactory = PsiFileFactory.getInstance(env.project) as PsiFileFactoryImpl
        val ktFile = psiFileFactory.trySetupPsiForFile(vFile, KotlinLanguage.INSTANCE, true, false) as KtFile

        val trace = CliBindingTrace()
        val analysisResult = TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
            env.project,
            listOf(ktFile),
            trace,
            configuration,
            env::createPackagePartProvider
        )
        if (analysisResult.isError()) {
            throw analysisResult.error
        }
        AnalyzingUtils.throwExceptionOnErrors(analysisResult.bindingContext)

        val genState = GenerationState.Builder(
            env.project,
            classBuilderFactory,
            analysisResult.moduleDescriptor,
            trace.bindingContext,
            listOf(ktFile),
            configuration
        ).codegenFactory(DefaultCodegenFactory).isIrBackend(false).build()
        KotlinCodegenFacade.compileCorrectFiles(genState)

        AnalyzingUtils.throwExceptionOnErrors(genState.collectedExtraJvmDiagnostics)

        val cl = MemoryClassLoader(KotlinCompileHelper::class.java.classLoader)
        for (outputFile in genState.factory.currentOutput) {
            cl.files[outputFile.relativePath] = outputFile.asByteArray()
        }
        Files.walk(outDir).filter(Files::isRegularFile).forEach { p ->
            cl.files[outDir.relativize(p).toString()] = Files.readAllBytes(p)
        }

        return Result(cl, cl.files.keys)
    }

    private fun getClasspath(cl: ClassLoader): List<File> =
        getClasspathSingle(cl) + (cl.parent?.let { getClasspath(it) } ?: emptyList())

    private fun getClasspathSingle(cl: ClassLoader): List<File> {
        if (cl is URLClassLoader) {
            return cl.urLs.map { File(it.toURI()) }
        }
        // ideally, we'd look at the system class loaders too (jdk.internal.loader.BuiltinClassLoader), but they're
        // protected from reflection in newer JDKs. So, we fall back to using the java.class.path system property in the
        // code above, and ignore those class loaders here.

        return emptyList()
    }

    private fun getClasspathFromSystemProperty(prop: String): List<File> {
        val value = System.getProperty(prop) ?: return emptyList()
        return value.split(System.getProperty("path.separator")).map { File(it) }
    }

    data class Result(
        val classLoader: ClassLoader,
        val fileNames: Collection<String>
    )

    private class MemoryClassLoader(parent: ClassLoader?) : ClassLoader(parent) {
        val files = mutableMapOf<String, ByteArray>()

        override fun findResource(name: String): URL? {
            val resource = files[name] ?: return null
            return URL("data:text/plain;base64," + Base64.getUrlEncoder().encodeToString(resource))
        }

        override fun findClass(name: String): Class<*> {
            val path = name.replace('.', '/') + ".class"
            val file = files[path] ?: throw ClassNotFoundException(name)
            return defineClass(name, file, 0, file.size)
        }
    }
}
package io.micronaut.kotlin.processing.aop.compile

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KspKt
import com.tschuchort.compiletesting.Ksp2Kt
import com.tschuchort.compiletesting.SourceFile
import io.micronaut.kotlin.processing.beans.BeanDefinitionProcessor
import groovy.transform.Immutable
import spock.lang.Specification

/**
 * KSP only keeps an output across incremental runs while one of its originating files is unchanged, so every output
 * of a bean must name the file that declares it.
 */
class IncrementalOriginatingFilesSpec extends Specification {

    void "the bean definition of an around advice proxy of a library type originates from the declaring file"() {
        given:
        def outputs = compile('SupplierFactory.kt', '''
package test

import io.micronaut.context.annotation.Factory
import io.micronaut.kotlin.processing.aop.simple.Mutating
import jakarta.inject.Singleton
import java.util.function.Supplier

@Factory
class SupplierFactory {
    @Singleton
    @Mutating("name")
    fun supplier(): Supplier<String> = Supplier { "value" }
}
''')

        expect:
        outputs.keySet().count { it.contains('$Intercepted$Definition') } == 2
        outputs.findAll { it.value.aggregating || !it.value.originatingFiles.contains('SupplierFactory.kt') } == [:]
    }

    void "the adapter of an event listener originates from the declaring file"() {
        given:
        def outputs = compile('StartupListener.kt', '''
package test

import io.micronaut.context.event.StartupEvent
import io.micronaut.runtime.event.annotation.EventListener
import jakarta.inject.Singleton

@Singleton
class StartupListener {
    @EventListener
    fun onStartup(event: StartupEvent) {
    }
}
''')

        expect:
        outputs.keySet().count { it.contains('$ApplicationEventListener$onStartup1$Intercepted') } == 4
        outputs.findAll { it.value.aggregating || !it.value.originatingFiles.contains('StartupListener.kt') } == [:]
    }

    private static Map<String, Output> compile(String fileName, String source) {
        Map<String, Output> outputs = [:]
        def compilation = new KotlinCompilation()
        Ksp2Kt.useKsp2(compilation)
        compilation.languageVersion = '2.0'
        compilation.kotlincArguments = ['-Xsuppress-version-warnings', '-Xannotation-default-target=first-only']
        compilation.classpaths = System.getProperty('java.class.path').split(File.pathSeparator)
            .findAll { !it.isEmpty() }
            .collect { new File(it) }
            .findAll { !it.name.startsWith('kotlin-compiler-embeddable-') && !it.name.startsWith('symbol-processing-aa-embeddable-') }
        compilation.sources = [SourceFile.@Companion.kotlin(fileName, source, true)]
        KspKt.setSymbolProcessorProviders(compilation, [new RecordingBeanDefinitionProcessorProvider(outputs)] as List<SymbolProcessorProvider>)
        def result = compilation.compile()
        assert result.exitCode == KotlinCompilation.ExitCode.OK: result.messages
        return outputs
    }

    /**
     * The dependencies of a generated file, read while its originating files are still valid.
     */
    @Immutable
    private static class Output {
        boolean aggregating
        List<String> originatingFiles

        static Output of(Dependencies dependencies) {
            new Output(dependencies.aggregating, dependencies.originatingFiles*.fileName)
        }
    }

    private static class RecordingBeanDefinitionProcessorProvider implements SymbolProcessorProvider {
        private final Map<String, Output> outputs

        RecordingBeanDefinitionProcessorProvider(Map<String, Output> outputs) {
            this.outputs = outputs
        }

        @Override
        SymbolProcessor create(SymbolProcessorEnvironment environment) {
            def codeGenerator = new RecordingCodeGenerator(environment.codeGenerator, outputs)
            return new BeanDefinitionProcessor(new SymbolProcessorEnvironment(
                environment.options,
                environment.kotlinVersion,
                codeGenerator,
                environment.logger,
                environment.apiVersion,
                environment.compilerVersion,
                environment.platforms,
                environment.kspVersion
            ))
        }
    }

    private static class RecordingCodeGenerator implements CodeGenerator {
        @Delegate
        private final CodeGenerator delegate
        private final Map<String, Output> outputs

        RecordingCodeGenerator(CodeGenerator delegate, Map<String, Output> outputs) {
            this.delegate = delegate
            this.outputs = outputs
        }

        @Override
        OutputStream createNewFile(Dependencies dependencies, String packageName, String fileName, String extensionName) {
            outputs["$packageName.$fileName.$extensionName".toString()] = Output.of(dependencies)
            return delegate.createNewFile(dependencies, packageName, fileName, extensionName)
        }

        @Override
        OutputStream createNewFileByPath(Dependencies dependencies, String path, String extensionName) {
            outputs["$path.$extensionName".toString()] = Output.of(dependencies)
            return delegate.createNewFileByPath(dependencies, path, extensionName)
        }
    }
}

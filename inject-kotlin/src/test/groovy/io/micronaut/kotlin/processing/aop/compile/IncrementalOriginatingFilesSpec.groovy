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
import io.micronaut.core.type.TypeHierarchy
import io.micronaut.kotlin.processing.beans.BeanDefinitionProcessor
import io.micronaut.kotlin.processing.visitor.TypeElementSymbolProcessor
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

    void "each file recording a type owns its record and the service entry of the record"() {
        given:
        def outputs = compileVisitors([
            'First.kt': '''
package owners

import io.micronaut.core.annotation.TypeHierarchyImport

@TypeHierarchyImport(classes = [java.util.ArrayList::class, Shared::class])
class First
''',
            'Second.kt': '''
package owners

import io.micronaut.core.annotation.TypeHierarchyImport

@TypeHierarchyImport(classes = [java.util.ArrayList::class, Shared::class])
class Second
''',
            'Shared.kt': '''
package owners

class Shared : java.util.function.Supplier<String> {
    override fun get(): String = ""
}
'''])
        def serviceDir = 'META-INF.micronaut.' + TypeHierarchy.name + File.separator
        def records = outputs.findAll { it.key.contains('$TypeHierarchy') }

        expect: "a class and its service entry per record, both from the file of the class that recorded the type, so that changing one file keeps the record of the other"
        records.keySet() == ['owners.$$java.util.$ArrayList$TypeHierarchy$$First', 'owners.$Shared$TypeHierarchy$$First',
                             'owners.$$java.util.$ArrayList$TypeHierarchy$$Second', 'owners.$Shared$TypeHierarchy$$Second']
            .collectMany { [it + '.class', serviceDir + it + '.'] } as Set
        records.every { !it.value.aggregating && it.value.originatingFiles == [it.key.contains('$$First') ? 'First.kt' : 'Second.kt'] }
    }

    private static Map<String, Output> compile(String fileName, String source) {
        return compile([(fileName): source]) { SymbolProcessorEnvironment environment -> new BeanDefinitionProcessor(environment) }
    }

    private static Map<String, Output> compileVisitors(Map<String, String> sources) {
        return compile(sources) { SymbolProcessorEnvironment environment -> new TypeElementSymbolProcessor(environment) }
    }

    private static Map<String, Output> compile(Map<String, String> sources, Closure<SymbolProcessor> processor) {
        Map<String, Output> outputs = [:]
        def compilation = new KotlinCompilation()
        Ksp2Kt.useKsp2(compilation)
        compilation.languageVersion = '2.4'
        compilation.kotlincArguments = ['-Xsuppress-version-warnings', '-Xannotation-default-target=first-only']
        compilation.classpaths = System.getProperty('java.class.path').split(File.pathSeparator)
            .findAll { !it.isEmpty() }
            .collect { new File(it) }
            .findAll { !it.name.startsWith('kotlin-compiler-embeddable-') && !it.name.startsWith('symbol-processing-aa-embeddable-') }
        compilation.sources = sources.collect { SourceFile.@Companion.kotlin(it.key, it.value, true) }
        KspKt.setSymbolProcessorProviders(compilation, [new RecordingProcessorProvider(outputs, processor)] as List<SymbolProcessorProvider>)
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

    private static class RecordingProcessorProvider implements SymbolProcessorProvider {
        private final Map<String, Output> outputs
        private final Closure<SymbolProcessor> processor

        RecordingProcessorProvider(Map<String, Output> outputs, Closure<SymbolProcessor> processor) {
            this.outputs = outputs
            this.processor = processor
        }

        @Override
        SymbolProcessor create(SymbolProcessorEnvironment environment) {
            def codeGenerator = new RecordingCodeGenerator(environment.codeGenerator, outputs)
            return processor.call(new SymbolProcessorEnvironment(
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

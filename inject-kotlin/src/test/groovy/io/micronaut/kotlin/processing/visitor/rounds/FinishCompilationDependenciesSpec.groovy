package io.micronaut.kotlin.processing.visitor.rounds

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import io.micronaut.annotation.processing.test.KotlinCompiler
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.rounds.RoundsVisitor
import spock.lang.Specification

/**
 * The bean definition and the service entry of a bean registered from
 * {@link TypeElementVisitor#finishCompilation(io.micronaut.inject.visitor.VisitorContext)} are aggregating outputs of
 * the files of every originating element, so that KSP writes them again, processing those files again, whenever a
 * source of the compilation changes, is added or is removed. The beans registered during the rounds stay isolating.
 */
class FinishCompilationDependenciesSpec extends Specification {

    private static final String ENTRIES = 'META-INF.micronaut.io.micronaut.inject.BeanDefinitionReference/'
    private static final String COMPILATION_DEFINITION = 'registerbean.$GeneratedLater$RegisteredRecord1$Definition'

    void setup() {
        RoundsVisitor.reset()
        RoundsVisitor.setRegisterInFinish(false)
    }

    void cleanup() {
        RoundsVisitor.reset()
    }

    void "the beans registered once every round is over are aggregating outputs of every originating file"() {
        given:
        Map<String, List<Object>> dependencies = [:]

        when:
        KotlinCompiler.compile([
                KotlinCompiler.kotlinSource('registerbean/Trigger.kt', 'package registerbean\n\nclass Trigger\n'),
                KotlinCompiler.kotlinSource('registerbean/Other.kt', 'package registerbean\n\nclass Other\n')
        ]) { SymbolProcessorEnvironment environment -> recording(environment, dependencies) }

        then:"the bean of the whole compilation is written again with any of its files"
        RoundsVisitor.EVENTS.contains('compilation[registerbean.GeneratedLater, registerbean.Other, registerbean.Trigger]')
        dependencies[COMPILATION_DEFINITION + '.class'] == [true, ['GeneratedLater.kt', 'Other.kt', 'Trigger.kt']]
        dependencies[ENTRIES + COMPILATION_DEFINITION] == [true, ['GeneratedLater.kt', 'Other.kt', 'Trigger.kt']]

        and:"the bean of the first round is an isolating output, and its service entry has its first file only"
        // the first round is named after the class KSP visits first
        String roundDefinition = dependencies.keySet().find {
            it in ['registerbean.$Trigger$RegisteredRecord0$Definition.class', 'registerbean.$Other$RegisteredRecord0$Definition.class']
        }
        String firstFile = roundDefinition.substring('registerbean.$'.length(), roundDefinition.indexOf('$RegisteredRecord')) + '.kt'
        dependencies[roundDefinition] == [false, ['Other.kt', 'Trigger.kt']]
        dependencies[ENTRIES + (roundDefinition - '.class')] == [false, [firstFile]]
    }

    private static SymbolProcessorEnvironment recording(SymbolProcessorEnvironment environment, Map<String, List<Object>> dependencies) {
        new SymbolProcessorEnvironment(
                environment.options,
                environment.kotlinVersion,
                new RecordingCodeGenerator(environment.codeGenerator, dependencies),
                environment.logger,
                environment.apiVersion,
                environment.compilerVersion,
                environment.platforms,
                environment.kspVersion,
                environment.registerProcessorForNewFeatures
        )
    }

    /**
     * Records the dependencies of every file it creates, by package, name and extension: whether the file is
     * aggregating, and the names of its originating files, read while they are valid.
     */
    static class RecordingCodeGenerator implements CodeGenerator {

        private final CodeGenerator delegate
        private final Map<String, List<Object>> dependencies

        RecordingCodeGenerator(CodeGenerator delegate, Map<String, List<Object>> dependencies) {
            this.delegate = delegate
            this.dependencies = dependencies
        }

        @Override
        OutputStream createNewFile(Dependencies dependencies, String packageName, String fileName, String extensionName) {
            record(packageName + '.' + fileName + (extensionName ? '.' + extensionName : ''), dependencies)
            return delegate.createNewFile(dependencies, packageName, fileName, extensionName)
        }

        @Override
        OutputStream createNewFileByPath(Dependencies dependencies, String path, String extensionName) {
            record(path + (extensionName ? '.' + extensionName : ''), dependencies)
            return delegate.createNewFileByPath(dependencies, path, extensionName)
        }

        private void record(String file, Dependencies dependencies) {
            this.dependencies.put(file, [dependencies.aggregating, dependencies.originatingFiles*.fileName.sort()])
        }

        @Override
        void associate(List<KSFile> sources, String packageName, String fileName, String extensionName) {
            delegate.associate(sources, packageName, fileName, extensionName)
        }

        @Override
        void associateByPath(List<KSFile> sources, String path, String extensionName) {
            delegate.associateByPath(sources, path, extensionName)
        }

        @Override
        void associateWithClasses(List<KSClassDeclaration> classes, String packageName, String fileName, String extensionName) {
            delegate.associateWithClasses(classes, packageName, fileName, extensionName)
        }

        @Override
        Collection<File> getGeneratedFile() {
            return delegate.getGeneratedFile()
        }
    }
}

package io.micronaut.inject.visitor.rounds

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.beanimport.naming.FilerRecordingProcessor
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.visitor.TypeElementVisitor
import spock.lang.Specification
import spock.lang.TempDir

import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * The bean definition and the service entry of a bean registered from
 * {@link TypeElementVisitor#finishCompilation(io.micronaut.inject.visitor.VisitorContext)} follow from the whole
 * compilation: they are written with every originating element, except to the filer of Gradle, which gets none, so
 * that an incremental build compiles every class again whenever a class changes, is added or is removed.
 *
 * <p>The incremental builds are simulated with the rules of Gradle's incremental compilation of an isolating
 * processor, which counts the top level types of the originating elements of each generated file: a file with exactly
 * one is deleted, and its type compiled again, when that type changes; a file with none or several makes the next
 * compilation a full one.</p>
 */
class FinishCompilationIncrementalSpec extends Specification {

    private static final String COMPILATION_DEFINITION = 'registerbean/$A$RegisteredRecord1$Definition.class'
    private static final String COMPILATION_ENTRY = 'META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/registerbean.$A$RegisteredRecord1$Definition'
    private static final String ROUND_DEFINITION = 'registerbean/$A$RegisteredRecord0$Definition.class'
    private static final String ROUND_ENTRY = 'META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/registerbean.$A$RegisteredRecord0$Definition'

    @TempDir
    Path tempDir

    void setup() {
        RoundsVisitor.reset()
        RoundsVisitor.setRegisterInFinish(false)
    }

    void cleanup() {
        RoundsVisitor.reset()
    }

    void "the definition and the service entry of a bean registered once every round is over follow from the whole compilation"() {
        when:
        Map<String, List<String>> gradle = compile(tempDir.resolve('gradle'), [A: source('A'), B: source('B')])
        Map<String, List<String>> javac = compile(tempDir.resolve('javac'), [A: source('A'), B: source('B')], false)

        then:"Gradle gets no originating element for the bean of the whole compilation, so it recompiles every class"
        gradle[COMPILATION_DEFINITION] == []
        gradle[COMPILATION_ENTRY] == []

        and:"another filer gets every class"
        javac[COMPILATION_DEFINITION] == ['registerbean.A', 'registerbean.B']
        javac[COMPILATION_ENTRY] == ['registerbean.A', 'registerbean.B']

        and:"the bean of a round still reports its first class only to Gradle"
        gradle[ROUND_DEFINITION] == ['registerbean.A']
        gradle[ROUND_ENTRY] == ['registerbean.A']
    }

    void "adding a class to a compilation of a single class writes the bean with both"() {
        given:"a bean of the whole compilation that follows from a single class"
        Path output = tempDir.resolve('classes')
        Map<String, String> sources = [A: source('A')]
        Map<String, List<String>> originatingTypes = compile(output, sources)

        when:
        sources.B = source('B')
        incrementalBuild(output, originatingTypes, sources, ['B'])

        then:
        compilationClasses(output) == ['registerbean.A', 'registerbean.B']
        compilationBeans(output) == 1
    }

    void "changing a class the bean follows from writes the bean of a full build"() {
        given:
        Path output = tempDir.resolve('classes')
        Map<String, String> sources = [A: source('A'), B: source('B')]
        Map<String, List<String>> originatingTypes = compile(output, sources)

        when:
        sources.B = source('B') + '\n// a change\n'
        incrementalBuild(output, originatingTypes, sources, ['B'])

        then:
        compilationClasses(output) == ['registerbean.A', 'registerbean.B']
        compilationBeans(output) == 1
    }

    void "removing a class the bean follows from writes the bean without it"() {
        given:
        Path output = tempDir.resolve('classes')
        Map<String, String> sources = [A: source('A'), B: source('B')]
        Map<String, List<String>> originatingTypes = compile(output, sources)

        when:
        sources.remove('B')
        incrementalBuild(output, originatingTypes, sources, ['B'])

        then:
        compilationClasses(output) == ['registerbean.A']
        compilationBeans(output) == 1
    }

    void "adding a class writes the bean with it"() {
        given:
        Path output = tempDir.resolve('classes')
        Map<String, String> sources = [A: source('A'), B: source('B')]
        Map<String, List<String>> originatingTypes = compile(output, sources)

        when:
        sources.C = source('C')
        incrementalBuild(output, originatingTypes, sources, ['C'])

        then:
        compilationClasses(output) == ['registerbean.A', 'registerbean.B', 'registerbean.C']
        compilationBeans(output) == 1
    }

    private static String source(String name) {
        """
package registerbean;

public class $name {
}
"""
    }

    /**
     * Compiles again the way Gradle's incremental compilation of an isolating processor does.
     */
    private void incrementalBuild(Path output, Map<String, List<String>> originatingTypes, Map<String, String> sources, List<String> changed) {
        if (originatingTypes.values().any { it.toUnique().size() != 1 }) {
            // "the generated type ... must have exactly one originating element", counting top level types: a full
            // recompilation
            output.toFile().deleteDir()
            compile(output, sources)
            return
        }
        for (String name : changed) {
            String type = 'registerbean.' + name
            Files.deleteIfExists(output.resolve("registerbean/${name}.class"))
            originatingTypes.each { path, types ->
                if (types == [type]) {
                    Files.deleteIfExists(output.resolve(path))
                }
            }
        }
        Map<String, String> changedSources = sources.subMap(changed)
        if (changedSources) {
            compile(output, changedSources)
        }
    }

    /**
     * Compiles the sources of the package {@code registerbean} into the output directory, which is also on the class
     * path, by default with a filer that the processor takes for the filer of Gradle.
     *
     * @return For each generated file, the top level types of the originating elements given to the filer
     */
    private Map<String, List<String>> compile(Path output, Map<String, String> sources, boolean gradle = true) {
        Path sourceDir = Files.createTempDirectory(tempDir, 'src')
        List<Path> files = sources.collect { name, code ->
            Path file = sourceDir.resolve("registerbean/${name}.java")
            Files.createDirectories(file.parent)
            Files.writeString(file, code)
            file
        }
        Files.createDirectories(output)
        def compiler = ToolProvider.systemJavaCompiler
        def diagnostics = new DiagnosticCollector<JavaFileObject>()
        def fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)
        try {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, [output])
            def options = [
                // the classes are not annotated, so the processor processes every class, not only annotated ones
                '-proc:full',
                '-classpath', output.toString() + File.pathSeparator + System.getProperty('java.class.path')
            ]
            def task = compiler.getTask(null, fileManager, diagnostics, options, null, fileManager.getJavaFileObjectsFromPaths(files))
            Map<String, List<String>> originatingTypes = [:]
            def processor = new TypeElementVisitorProcessor() {
                @Override
                protected Collection<TypeElementVisitor> findTypeElementVisitors() {
                    return [new RoundsVisitor()]
                }
            }
            task.setProcessors([new FilerRecordingProcessor(processor, originatingTypes, gradle)])
            boolean success = task.call()
            assert success: diagnostics.diagnostics.findAll { it.kind == Diagnostic.Kind.ERROR }.join('\n')
            return originatingTypes
        } finally {
            fileManager.close()
        }
    }

    private static List<String> compilationClasses(Path output) {
        withContext(output) { ApplicationContext context ->
            context.getBeanDefinition(RegisteredRecord, Qualifiers.byName('compilation')).stringValues(Recorded, 'classes') as List
        }
    }

    private static int compilationBeans(Path output) {
        withContext(output) { ApplicationContext context ->
            context.getBeanDefinitions(RegisteredRecord, Qualifiers.byName('compilation')).size()
        }
    }

    private static <T> T withContext(Path output, Closure<T> closure) {
        new URLClassLoader([output.toUri().toURL()] as URL[], FinishCompilationIncrementalSpec.classLoader).withCloseable { classLoader ->
            ApplicationContext.builder().classLoader(classLoader).start().withCloseable(closure)
        }
    }
}

package io.micronaut.inject.beanimport.naming

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.beanimport.naming.first.Library
import org.codehaus.groovy.tools.FileSystemCompiler
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

/**
 * The definitions of imported beans are named after the importer and the imported type only, see
 * https://github.com/micronaut-projects/micronaut-core/issues/13635.
 *
 * <p>Most compilations load the compiler and the AST transformations anew, as Gradle does. The others reuse the
 * classes this JVM already loaded, as the test kit does.</p>
 */
class ImportedBeanDefinitionNamesSpec extends Specification {

    private static final String REFERENCES = 'META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference'

    private static final String APPLICATION = '''
package example

import io.micronaut.context.annotation.Import
import io.micronaut.inject.beanimport.naming.first.Library

@Import(classes = Library)
class Application {
}
'''

    private static final String OTHER = APPLICATION.replace('class Application', 'class Other')

    private static final Set<String> BOTH_DEFINITIONS = [
        'example.$Application$Library0$Definition',
        'example.$Other$Library0$Definition'
    ] as Set

    @TempDir
    Path tempDir

    void "compiling the same sources again with the loaded classes gives the same names"() {
        given:
        Path first = tempDir.resolve('first')
        Path second = tempDir.resolve('second')

        when:
        compileWithLoadedClasses(first, ['example/Application.groovy': APPLICATION, 'example/Other.groovy': OTHER])
        compileWithLoadedClasses(second, ['example/Application.groovy': APPLICATION, 'example/Other.groovy': OTHER])

        then:
        definitions(first) == BOTH_DEFINITIONS
        generatedFiles(second) == generatedFiles(first)
    }

    void "the order of the sources does not change the names"() {
        given:
        Path inOrder = tempDir.resolve('in-order')
        Path reversed = tempDir.resolve('reversed')

        when:
        compile(inOrder, ['example/Application.groovy': APPLICATION, 'example/Other.groovy': OTHER])
        compile(reversed, ['example/Other.groovy': OTHER, 'example/Application.groovy': APPLICATION])

        then:
        definitions(inOrder) == BOTH_DEFINITIONS
        generatedFiles(reversed) == generatedFiles(inOrder)
    }

    void "imported classes with the same simple name get their own definitions"() {
        given:
        Path output = tempDir.resolve('classes')

        when:
        compile(output, ['example/Both.groovy': '''
package example

import io.micronaut.context.annotation.Import

@Import(classes = [
    io.micronaut.inject.beanimport.naming.first.Library,
    io.micronaut.inject.beanimport.naming.second.Library
])
class Both {
}
'''])

        then:
        definitions(output) == ['example.$Both$Library0$Definition', 'example.$Both$Library1$Definition'] as Set
        beanCount(output, io.micronaut.inject.beanimport.naming.first.Library) == 1
        beanCount(output, io.micronaut.inject.beanimport.naming.second.Library) == 1
    }

    void "imported packages with classes of the same simple name get their own definitions"() {
        given:
        Path output = tempDir.resolve('classes')

        when:
        compile(output, ['example/Packages.groovy': '''
package example

import io.micronaut.context.annotation.Import

@Import(packages = [
    "io.micronaut.inject.beanimport.naming.first",
    "io.micronaut.inject.beanimport.naming.second"
], annotated = "*")
class Packages {
}
'''])

        then:
        definitions(output) == ['example.$Packages$Library0$Definition', 'example.$Packages$Library1$Definition'] as Set
        beanCount(output, io.micronaut.inject.beanimport.naming.first.Library) == 1
        beanCount(output, io.micronaut.inject.beanimport.naming.second.Library) == 1
    }

    void "a class imported by name and by package gets one definition"() {
        given:
        Path output = tempDir.resolve('classes')

        when:
        compile(output, ['example/Twice.groovy': '''
package example

import io.micronaut.context.annotation.Import
import io.micronaut.inject.beanimport.naming.first.Library

@Import(classes = Library, packages = "io.micronaut.inject.beanimport.naming.first", annotated = "*")
class Twice {
}
'''])

        then:
        definitions(output) == ['example.$Twice$Library0$Definition'] as Set
        beanCount(output, Library) == 1
    }

    void "recompiling only the importer that changed gives the definitions of a full build"() {
        given:
        Path output = tempDir.resolve('classes')
        compile(output, ['example/Application.groovy': APPLICATION, 'example/Other.groovy': OTHER])

        when: 'the changed source is compiled alone against the previous output, after its class files are deleted'
        Files.delete(output.resolve('example/Other.class'))
        compile(output, ['example/Other.groovy': OTHER + '\n// a change\n'])

        then:
        definitions(output) == BOTH_DEFINITIONS
        beanCount(output, Library) == 2
    }

    /**
     * Compiles the sources into the output directory, which is also on the class path. The compiler and the AST
     * transformations are loaded anew, as Gradle does.
     */
    private void compile(Path output, Map<String, String> sources) {
        URL[] classPath = System.getProperty('java.class.path').split(File.pathSeparator).collect { new File(it).toURI().toURL() } as URL[]
        new URLClassLoader(classPath, ClassLoader.platformClassLoader).withCloseable {
            groovyc(output, sources, it)
        }
    }

    /**
     * Compiles the sources into the output directory, which is also on the class path. The compiler and the AST
     * transformations are the classes this JVM already loaded, as the test kit uses them.
     */
    private void compileWithLoadedClasses(Path output, Map<String, String> sources) {
        groovyc(output, sources, ImportedBeanDefinitionNamesSpec.classLoader)
    }

    private void groovyc(Path output, Map<String, String> sources, ClassLoader classLoader) {
        Path sourceDir = Files.createTempDirectory(tempDir, 'src')
        List<String> files = sources.collect { path, code ->
            Path file = sourceDir.resolve(path)
            Files.createDirectories(file.parent)
            Files.writeString(file, code)
            file.toString()
        }
        Files.createDirectories(output)
        String[] args = ['-d', output.toString(), '-cp', output.toString() + File.pathSeparator + System.getProperty('java.class.path')] + files
        Thread thread = Thread.currentThread()
        ClassLoader contextClassLoader = thread.contextClassLoader
        thread.contextClassLoader = classLoader
        try {
            classLoader.loadClass(FileSystemCompiler.name)
                .getMethod('commandLineCompile', String[])
                .invoke(null, [args] as Object[])
        } finally {
            thread.contextClassLoader = contextClassLoader
        }
    }

    private static Set<String> definitions(Path output) {
        Path references = output.resolve(REFERENCES)
        if (!Files.isDirectory(references)) {
            return [] as Set
        }
        Files.list(references).withCloseable { it.map { it.fileName.toString() }.collect(Collectors.toSet()) }
    }

    /**
     * @return The relative paths of the generated files
     */
    private static Set<String> generatedFiles(Path output) {
        Files.walk(output).withCloseable { paths ->
            paths.filter { Files.isRegularFile(it) }
                .map { output.relativize(it).toString() }
                .filter { it.contains('$') || it.startsWith('META-INF') }
                .collect(Collectors.toSet())
        }
    }

    private static int beanCount(Path output, Class<?> beanType) {
        new URLClassLoader([output.toUri().toURL()] as URL[], ImportedBeanDefinitionNamesSpec.classLoader).withCloseable { classLoader ->
            ApplicationContext.builder().classLoader(classLoader).start().withCloseable { context ->
                context.getBeansOfType(beanType).size()
            }
        }
    }
}

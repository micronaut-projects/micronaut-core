package io.micronaut.inject.beanimport.naming

import io.micronaut.annotation.processing.AggregatingTypeElementVisitorProcessor
import io.micronaut.annotation.processing.BeanDefinitionInjectProcessor
import io.micronaut.annotation.processing.MixinVisitorProcessor
import io.micronaut.annotation.processing.PackageElementVisitorProcessor
import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.beanbuilder.ApplyAopToTypeVisitor
import io.micronaut.inject.beanimport.naming.first.Library
import io.micronaut.inject.visitor.TypeElementVisitor
import spock.lang.Specification
import spock.lang.TempDir

import javax.annotation.processing.Processor
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

/**
 * The definitions of imported beans are named after the importer and the imported type only, and originate from the
 * importer, see https://github.com/micronaut-projects/micronaut-core/issues/13635.
 *
 * <p>Most compilations load the processor classes anew, as Gradle and {@code javac -processorpath} do. The others
 * reuse the processor classes this JVM already loaded, as the test kit does.</p>
 */
class ImportedBeanDefinitionNamesSpec extends Specification {

    private static final String REFERENCES = 'META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference'

    private static final List<String> PROCESSORS = [
        MixinVisitorProcessor,
        PackageElementVisitorProcessor,
        TypeElementVisitorProcessor,
        AggregatingTypeElementVisitorProcessor,
        BeanDefinitionInjectProcessor
    ]*.name

    private static final String APPLICATION = '''
package example;

import io.micronaut.context.annotation.Import;
import io.micronaut.inject.beanimport.naming.first.Library;

@Import(classes = Library.class)
public class Application {
}
'''

    private static final String OTHER = APPLICATION.replace('class Application', 'class Other')

    private static final Set<String> BOTH_DEFINITIONS = [
        'example.$Application$Library0$Definition',
        'example.$Other$Library0$Definition'
    ] as Set

    @TempDir
    Path tempDir

    void "compiling the same sources again with the loaded processor classes gives the same names"() {
        given:
        Path first = tempDir.resolve('first')
        Path second = tempDir.resolve('second')

        when:
        compileWithLoadedProcessors(first, ['example/Application.java': APPLICATION, 'example/Other.java': OTHER])
        compileWithLoadedProcessors(second, ['example/Application.java': APPLICATION, 'example/Other.java': OTHER])

        then:
        definitions(first) == BOTH_DEFINITIONS
        generatedFiles(second) == generatedFiles(first)
    }

    void "the test kit gives the same names in every compilation"() {
        when:
        List<String> first = generatedClasses(APPLICATION)
        List<String> second = generatedClasses(APPLICATION)

        then:
        first.contains('example/$Application$Library0$Definition.class')
        second == first
    }

    void "the order of the sources does not change the names"() {
        given:
        Path inOrder = tempDir.resolve('in-order')
        Path reversed = tempDir.resolve('reversed')

        when:
        compile(inOrder, ['example/Application.java': APPLICATION, 'example/Other.java': OTHER])
        compile(reversed, ['example/Other.java': OTHER, 'example/Application.java': APPLICATION])

        then:
        definitions(inOrder) == BOTH_DEFINITIONS
        generatedFiles(reversed) == generatedFiles(inOrder)
    }

    void "imported classes with the same simple name get their own definitions"() {
        given:
        Path output = tempDir.resolve('classes')

        when:
        compile(output, ['example/Both.java': '''
package example;

import io.micronaut.context.annotation.Import;

@Import(classes = {
    io.micronaut.inject.beanimport.naming.first.Library.class,
    io.micronaut.inject.beanimport.naming.second.Library.class
})
public class Both {
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
        compile(output, ['example/Packages.java': '''
package example;

import io.micronaut.context.annotation.Import;

@Import(packages = {
    "io.micronaut.inject.beanimport.naming.first",
    "io.micronaut.inject.beanimport.naming.second"
}, annotated = "*")
public class Packages {
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
        compile(output, ['example/Twice.java': '''
package example;

import io.micronaut.context.annotation.Import;
import io.micronaut.inject.beanimport.naming.first.Library;

@Import(classes = Library.class, packages = "io.micronaut.inject.beanimport.naming.first", annotated = "*")
public class Twice {
}
'''])

        then:
        definitions(output) == ['example.$Twice$Library0$Definition'] as Set
        beanCount(output, Library) == 1
    }

    void "the files generated for an imported bean originate from the importer"() {
        given:
        Path output = tempDir.resolve('classes')

        when:
        Map<String, List<String>> originatingTypes = compile(output, ['example/Application.java': APPLICATION, 'example/Other.java': OTHER])

        then:
        originatingTypes.findAll { path, types -> path.contains('$Application$Library') } == [
            'example/$Application$Library0$Definition.class': ['example.Application'],
            (REFERENCES + '/example.$Application$Library0$Definition'): ['example.Application']
        ]
        originatingTypes.findAll { path, types -> path.contains('$Other$Library') } == [
            'example/$Other$Library0$Definition.class': ['example.Other'],
            (REFERENCES + '/example.$Other$Library0$Definition'): ['example.Other']
        ]
    }

    void "recompiling only the importer that changed gives the definitions of a full build"() {
        given:
        Path output = tempDir.resolve('classes')
        Map<String, List<String>> originatingTypes = compile(output, ['example/Application.java': APPLICATION, 'example/Other.java': OTHER])

        when:
        recompile(output, originatingTypes, 'example.Other', OTHER + '\n// a change\n')

        then:
        definitions(output) == BOTH_DEFINITIONS
        beanCount(output, Library) == 2
    }

    void "recompiling an importer without @Import removes its definition"() {
        given:
        Path output = tempDir.resolve('classes')
        Map<String, List<String>> originatingTypes = compile(output, ['example/Application.java': APPLICATION, 'example/Other.java': OTHER])

        when:
        recompile(output, originatingTypes, 'example.Other', '''
package example;

public class Other {
}
''')

        then:
        definitions(output) == ['example.$Application$Library0$Definition'] as Set
        beanCount(output, Library) == 1
    }

    void "the classes generated for an intercepted associated bean keep their names and originate from the type that added the bean"() {
        given:
        Path first = tempDir.resolve('first')
        Path second = tempDir.resolve('second')
        Map<String, String> sources = [
            'aopbuilder/Test.java': '''
package aopbuilder;

import io.micronaut.inject.beanbuilder.ApplyAopToMe;
import jakarta.inject.Singleton;

@Singleton
class Test {
    final ApplyAopToMe applyAopToMe;

    Test(ApplyAopToMe applyAopToMe) {
        this.applyAopToMe = applyAopToMe;
    }
}
''',
            'aopbuilder/Mutating.java': '''
package aopbuilder;

import io.micronaut.aop.Around;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Around
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@interface Mutating {
    String value();
}
''',
            'aopbuilder/MutatingInterceptor.java': '''
package aopbuilder;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;

@InterceptorBean(Mutating.class)
class MutatingInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }
}
'''
        ]

        when:
        Map<String, List<String>> originatingTypes = compileWithLoadedProcessors(first, sources, [new ApplyAopToTypeVisitor()])
        compileWithLoadedProcessors(second, sources, [new ApplyAopToTypeVisitor()])
        Map<String, List<String>> associated = originatingTypes.findAll { path, types -> path.contains('$Test$ApplyAopToMe') }

        then:
        associated.keySet() == [
            'aopbuilder/$Test$ApplyAopToMe0$Definition.class',
            'aopbuilder/$Test$ApplyAopToMe0$Definition$Exec.class',
            'aopbuilder/$Test$ApplyAopToMe0$Definition$Intercepted.class',
            'aopbuilder/$Test$ApplyAopToMe0$Definition$Intercepted$Definition.class',
            REFERENCES + '/aopbuilder.$Test$ApplyAopToMe0$Definition',
            REFERENCES + '/aopbuilder.$Test$ApplyAopToMe0$Definition$Intercepted$Definition'
        ] as Set
        associated.values().every { it[0] == 'aopbuilder.Test' }
        generatedFiles(second) == generatedFiles(first)
    }

    /**
     * Recompiles one changed source the way a Gradle incremental build does: it deletes the class files of the source
     * and the files generated from it, then compiles the source alone against the previous output.
     */
    private void recompile(Path output, Map<String, List<String>> originatingTypes, String type, String source) {
        Files.delete(output.resolve(type.replace('.', '/') + '.class'))
        originatingTypes.each { path, types ->
            // Micronaut passes the filer of Gradle the first originating element only
            if (types && types[0] == type) {
                Files.delete(output.resolve(path))
            }
        }
        compile(output, [(type.replace('.', '/') + '.java'): source])
    }

    /**
     * Compiles the sources into the output directory, which is also on the class path. The processor classes are
     * loaded anew, as Gradle and {@code javac -processorpath} do.
     *
     * @return For each generated file, the top level types of its originating elements
     */
    private Map<String, List<String>> compile(Path output, Map<String, String> sources) {
        URL[] classPath = System.getProperty('java.class.path').split(File.pathSeparator).collect { new File(it).toURI().toURL() } as URL[]
        new URLClassLoader(classPath, ClassLoader.platformClassLoader).withCloseable { processorClassLoader ->
            javac(output, sources) { Map<String, List<String>> originatingTypes ->
                PROCESSORS.collect {
                    Processor processor = (Processor) processorClassLoader.loadClass(it).getDeclaredConstructor().newInstance()
                    (Processor) processorClassLoader.loadClass(FilerRecordingProcessor.name)
                        .getConstructor(Processor, Map)
                        .newInstance(processor, originatingTypes)
                }
            }
        }
    }

    /**
     * Compiles the sources into the output directory, which is also on the class path. The processors are new
     * instances of the processor classes this JVM already loaded, as the test kit passes them.
     *
     * @return For each generated file, the top level types of its originating elements
     */
    private Map<String, List<String>> compileWithLoadedProcessors(Path output, Map<String, String> sources, Collection<TypeElementVisitor> visitors = null) {
        javac(output, sources) { Map<String, List<String>> originatingTypes ->
            TypeElementVisitorProcessor typeElementVisitorProcessor = visitors == null ? new TypeElementVisitorProcessor() : new TypeElementVisitorProcessor() {
                @Override
                protected Collection<TypeElementVisitor> findTypeElementVisitors() {
                    return visitors
                }
            }
            List<Processor> processors = [
                new MixinVisitorProcessor(),
                new PackageElementVisitorProcessor(),
                typeElementVisitorProcessor,
                new AggregatingTypeElementVisitorProcessor(),
                new BeanDefinitionInjectProcessor()
            ]
            processors.collect { (Processor) new FilerRecordingProcessor(it, originatingTypes) }
        }
    }

    private Map<String, List<String>> javac(Path output, Map<String, String> sources, Closure<List<Processor>> processors) {
        Path sourceDir = Files.createTempDirectory(tempDir, 'src')
        List<Path> files = sources.collect { path, code ->
            Path file = sourceDir.resolve(path)
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
                '-proc:full',
                '-parameters',
                '-Amicronaut.processing.incremental=true',
                '-classpath', output.toString() + File.pathSeparator + System.getProperty('java.class.path')
            ]
            def task = compiler.getTask(null, fileManager, diagnostics, options, null, fileManager.getJavaFileObjectsFromPaths(files))
            Map<String, List<String>> originatingTypes = [:]
            task.setProcessors(processors.call(originatingTypes))
            boolean success = task.call()
            assert success: diagnostics.diagnostics.findAll { it.kind == Diagnostic.Kind.ERROR }.join('\n')
            return originatingTypes
        } finally {
            fileManager.close()
        }
    }

    private static List<String> generatedClasses(String source) {
        new JavaParser().withCloseable { parser ->
            parser.generate('example.Application', source)
                .collect { it.name }
                .findAll { it.contains('example/') && it.endsWith('.class') }
                .collect { it.substring(it.indexOf('example/')) }
                .sort()
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

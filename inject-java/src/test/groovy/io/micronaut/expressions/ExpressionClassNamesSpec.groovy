package io.micronaut.expressions

import com.sun.source.util.JavacTask
import io.micronaut.annotation.processing.AggregatingTypeElementVisitorProcessor
import io.micronaut.annotation.processing.BeanDefinitionInjectProcessor
import io.micronaut.annotation.processing.MixinVisitorProcessor
import io.micronaut.annotation.processing.PackageElementVisitorProcessor
import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaFileObjects
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinitionReference
import spock.lang.TempDir

import javax.tools.DiagnosticCollector
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.StandardJavaFileManager
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class ExpressionClassNamesSpec extends AbstractTypeElementSpec {

    private static final String FOO = '''
        package test;
        import io.micronaut.context.annotation.Value;
        import jakarta.inject.Singleton;

        @Singleton
        public class Foo {
            public final String a;

            @Value("#{ 'b' }")
            public String b;

            public Foo(@Value("#{ 'a' }") String a) {
                this.a = a;
            }
        }
    '''

    private static final String BAR = '''
        package test;
        import jakarta.inject.Singleton;

        @Singleton
        public class Bar extends Foo {
            public Bar() {
                super("bar");
            }
        }
    '''

    @TempDir
    Path dir

    void "an expression class is named after the class, the element, the annotation and the member"() {
        when:
        List<String> names = expressionClassNames(buildClassLoader("test.Named", """
            package test;
            import io.micronaut.context.annotation.Value;
            import jakarta.inject.Inject;
            import jakarta.inject.Singleton;

            @Singleton
            class Named {
                @Value("#{ 1 + 1 }")
                public int first;

                Named(@Value("#{ 2 + 2 }") int second) {
                }

                @Inject
                void third(String ignored, @Value("#{ 3 + 3 }") int third) {
                }
            }
        """), 'test.$Named$Expr')

        then:
        names.size() == 3
        names.any { hashed(it, 'test.$Named$Expr$field$first$Value$value$') }
        names.any { hashed(it, 'test.$Named$Expr$constructor$param0$Value$value$') }
        names.any { hashed(it, 'test.$Named$Expr$method$third$param1$Value$value$') }
    }

    void "a class compiled again by processors whose classes stay loaded keeps the names of its expression classes"() {
        given: "the processors of this JVM, which compile every class of this spec, as a build tool's daemon keeps them"
        String source = """
            package test;
            import io.micronaut.context.annotation.Value;
            import jakarta.inject.Singleton;

            @Singleton
            class Again {
                @Value("#{ 1 + 1 }")
                public int first;

                @Value("#{ 2 + 2 }")
                public int second;
            }
        """

        when:
        List<String> first = expressionClassNames(buildClassLoader("test.Again", source), 'test.$Again$Expr')
        List<String> second = expressionClassNames(buildClassLoader("test.Again", source), 'test.$Again$Expr')

        then:
        first.size() == 2
        first.toSorted() == second.toSorted()
    }

    void "overloads, repeated annotations and long names give distinct names that fit a file name"() {
        given:
        String longName = 'x' * 250
        String wideName = '\u540d' * 100

        when:
        List<String> names = expressionClassNames(buildClassLoader("test.Distinct", """
            package test;
            import io.micronaut.context.annotation.Property;
            import io.micronaut.context.annotation.Value;
            import jakarta.inject.Inject;
            import jakarta.inject.Singleton;

            @Singleton
            @Property(name = "one", value = "#{ 'same' }")
            @Property(name = "two", value = "#{ 'other' }")
            class Distinct {
                @Value("#{ 'same' }")
                public String $longName;

                @Value("#{ 'wide' }")
                public String $wideName;

                @Inject
                void set(@Value("#{ 'same' }") String value) {
                }

                @Inject
                void set(@Value("#{ 'same' }") Integer value) {
                }
            }
        """), 'test.$Distinct$Expr')

        then: "each expression has a class of its own"
        names.size() == 6
        names.toSet().size() == 6
        names.count { it.startsWith('test.$Distinct$Expr$type$Property$value$') } == 2
        names.count { it.startsWith('test.$Distinct$Expr$method$set$param0$Value$value$') } == 2

        and: "a name too long for a file, in characters or in the bytes of its file name, is cut short, the hash at its end telling it apart"
        [names.find { it.contains('$field$xxx') }, names.find { it.contains('$field$\u540d') }].every { String name ->
            name.substring('test.'.length()).getBytes(StandardCharsets.UTF_8).length <= 200
                    && name.substring(name.length() - 17) ==~ /[$]\p{XDigit}{16}/
        }
    }

    void "a subclass compiled against the class files of its superclass neither writes nor overwrites the expression classes of the superclass"() {
        given: "Foo compiled on its own, with an expression on a constructor parameter, which a subclass does not inherit, and one on a field"
        Path fooOutput = Files.createDirectories(dir.resolve("foo"))
        compile(fooOutput, [], ['test.Foo': FOO])
        List<String> fooExpressions = classNames(fooOutput).findAll { it.startsWith('test.$Foo$Expr') }

        expect:
        fooExpressions.size() == 2

        when: "Bar, which inherits the field, compiles on its own against the class files of Foo"
        Path barOutput = Files.createDirectories(dir.resolve("bar"))
        compile(barOutput, [fooOutput], ['test.Bar': BAR])

        then: "the expression classes of Foo, which the compilation of Foo wrote, are not written again"
        classNames(barOutput).findAll { it.contains('$Expr') }.isEmpty()
        classNames(barOutput).contains('test.$Bar$Definition')

        and: "both evaluate the expressions they declare"
        assertValues([fooOutput, barOutput])
    }

    void "a subclass writes the expression classes of a superclass whose compilation did not write them as named now"() {
        given: "Foo compiled by a version that numbered its expression classes, as a library may have been"
        Path fooOutput = Files.createDirectories(dir.resolve("foo"))
        compile(fooOutput, [], ['test.Foo': FOO])
        classNames(fooOutput).findAll { it.startsWith('test.$Foo$Expr') }.each {
            Files.delete(fooOutput.resolve(it.replace('.', '/') + '.class'))
        }

        when:
        Path barOutput = Files.createDirectories(dir.resolve("bar"))
        compile(barOutput, [fooOutput], ['test.Bar': BAR])

        then: "Bar's compilation writes the class of the expression it inherits"
        classNames(barOutput).findAll { it.startsWith('test.$Foo$Expr$field$b$Value$value$') }.size() == 1
    }

    private void assertValues(List<Path> outputs) {
        def loader = new URLClassLoader(outputs.collect { it.toUri().toURL() } as URL[], getClass().classLoader)
        def builder = ApplicationContext.builder().classLoader(loader).environments("test")
        builder.beanDefinitionsProvider {
            ['test.$Foo$Definition', 'test.$Bar$Definition'].collect {
                (BeanDefinitionReference) loader.loadClass(it).getDeclaredConstructor().newInstance()
            } + getBuiltInBeanReferences()
        }
        try (ApplicationContext context = builder.build().start()) {
            Class<?> fooType = loader.loadClass('test.Foo')
            Collection<Object> beans = context.getBeansOfType(fooType)
            Object foo = beans.find { it.getClass() == fooType }
            Object bar = beans.find { it.getClass().name == 'test.Bar' }
            assert foo.a == 'a'
            assert foo.b == 'b'
            assert bar.a == 'bar'
            assert bar.b == 'b'
        }
    }

    private static void compile(Path output, List<Path> classpath, Map<String, String> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler()
        def diagnostics = new DiagnosticCollector<JavaFileObject>()
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, [output])
            fileManager.setLocationFromPaths(StandardLocation.CLASS_PATH,
                    classpath + System.getProperty("java.class.path").split(File.pathSeparator).collect { Path.of(it) })
            def files = sources.collect { name, code -> JavaFileObjects.forSourceString(name, code) }
            def task = (JavacTask) compiler.getTask(null, fileManager, diagnostics, ["-proc:full"], null, files)
            task.setProcessors([
                    new MixinVisitorProcessor(),
                    new PackageElementVisitorProcessor(),
                    new TypeElementVisitorProcessor(),
                    new AggregatingTypeElementVisitorProcessor(),
                    new BeanDefinitionInjectProcessor()
            ])
            assert task.call(): diagnostics.diagnostics.join('\n')
        }
    }

    private static List<String> classNames(Path output) {
        try (def files = Files.walk(output)) {
            return files.filter { it.toString().endsWith('.class') }
                    .map { output.relativize(it).toString().replace(File.separatorChar, '.' as char) - '.class' }
                    .toList()
        }
    }

    private static boolean hashed(String name, String prefix) {
        return name.startsWith(prefix) && name.substring(prefix.length()) ==~ /\p{XDigit}{16}/
    }

    private static List<String> expressionClassNames(ClassLoader loader, String prefix) {
        return loader.classNames.findAll { String it -> it.startsWith(prefix) }
    }
}

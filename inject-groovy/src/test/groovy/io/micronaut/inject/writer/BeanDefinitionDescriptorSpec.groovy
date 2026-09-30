package io.micronaut.inject.writer

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import spock.lang.Shared
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class BeanDefinitionDescriptorSpec extends AbstractBeanDefinitionDescriptorSpec {

    @Shared
    @TempDir
    Path tempDir

    @Override
    protected URL[] compile() {
        Path targetDir = Files.createDirectories(tempDir.resolve('classes'))
        compileTo(targetDir, BEANS)
        return [targetDir.toUri().toURL()] as URL[]
    }

    @Override
    protected List<String> getModuleDefinitions() {
        // compiled with the test classes of the module: a bean, a bean of a factory and a proxy
        return [
            'io.micronaut.aop.simple.$AnotherClass$Definition',
            'io.micronaut.aop.named.$NamedFactory$NamedInterface0$Definition',
            'io.micronaut.aop.named.$NamedFactory$NamedInterface0$Definition$Intercepted$Definition'
        ]
    }

    @Override
    protected List<String> getArrayClassNames() {
        // the class of an array of a primitive type is given a name that no class has, and the definition holds the
        // value by that name
        return ['[Lint;', '[Ljava.lang.Object;']
    }

    @Override
    protected List<Class<?>> getEmptyArrayTypes() {
        // an empty array of an annotation of the same compilation is one of objects
        return [String[], int[], Object[]]
    }

    void "a definition built by a type element visitor has a descriptor"() {
        given: "the definition of the class that @Import makes a bean, which the transform of the visitors writes"
        def descriptor = descriptor('Imports$Library0')

        expect:
        descriptor.beanType() == 'test.Library'
        descriptor.exposedTypes() == ['test.Library']
    }

    void "an entry that is compiled again is written anew"() {
        given:
        Path targetDir = Files.createDirectories(tempDir.resolve('again'))
        Path entry = targetDir.resolve('META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/again.$Bean$Definition')

        when: "a bean with requirements, which its descriptor records"
        compileTo(targetDir, '''
package again

@jakarta.inject.Singleton
@io.micronaut.context.annotation.Requires(property = "a.property.with.a.long.name", value = "a value")
class Bean {
}
''')
        int length = Files.size(entry)

        then:
        BeanDefinitionDescriptor.read(Files.readAllBytes(entry)).preLoadConditions().size() == 1

        when: "the requirements are removed and the class is compiled over the classes of before"
        compileTo(targetDir, '''
package again

@jakarta.inject.Singleton
class Bean {
}
''')

        then: "the entry is the shorter descriptor and nothing of the longer one"
        Files.size(entry) < length
        BeanDefinitionDescriptor.read(Files.readAllBytes(entry)).preLoadConditions().isEmpty()
    }

    /**
     * Compiles to a directory, as the Groovy compiler of a build does.
     */
    private void compileTo(Path targetDir, String source) {
        Path sourceDir = Files.createTempDirectory(tempDir, 'src')
        Path sourceFile = Files.writeString(sourceDir.resolve('Beans.groovy'), source)
        def configuration = new CompilerConfiguration()
        configuration.targetDirectory = targetDir.toFile()
        def unit = new CompilationUnit(configuration, null, new GroovyClassLoader(getClass().classLoader, configuration))
        unit.addSource(sourceFile.toFile())
        unit.compile()
    }

    private static final String BEANS = '''
package test

import io.micronaut.aop.Around
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Import
import io.micronaut.context.annotation.NonBinding
import io.micronaut.context.annotation.Parallel
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Indexed
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Named
import jakarta.inject.Qualifier
import jakarta.inject.Singleton

import java.lang.annotation.Repeatable
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

interface Api {
}

interface Other {
}

@Singleton
class Plain implements Api {
}

@Context
class Eager {
}

@Parallel
@Singleton
class InParallel {
}

@Primary
@Singleton
class First implements Api {
}

@Prototype
class Each {
}

@Singleton
@Bean(typed = Api)
@Indexed(Other)
class Typed implements Api, Other {
}

@ConfigurationProperties("descriptor.settings")
class Settings {
    String name
}

@Singleton
class Startup {
    @Executable(processOnStartup = true)
    void run() {
    }
}

@Introspected
class Data {
}

@Retention(RetentionPolicy.RUNTIME)
@interface Marker {
}

@Marker
@Singleton
@Retention(RetentionPolicy.RUNTIME)
@interface Stereotyped {
}

@Retention(RetentionPolicy.SOURCE)
@interface Draft {
}

@Repeatable(Tags)
@Retention(RetentionPolicy.RUNTIME)
@interface Tag {
    String value()
}

@Retention(RetentionPolicy.RUNTIME)
@interface Tags {
    Tag[] value()
}

@Stereotyped
@Draft
@Tag("a")
@Tag("b")
class Tagged {
}

enum Mode {
    SOLID, STRIPED
}

@Retention(RetentionPolicy.RUNTIME)
@interface Detail {
    String value()
}

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@interface Colored {
    String name()

    int shade() default 0

    boolean dark() default false

    Mode mode() default Mode.SOLID

    Class<?> type() default Object

    String[] tags() default []

    int[] levels() default []

    Class<?>[] types() default []

    Detail detail() default @Detail("none")

    Detail[] details() default []

    @NonBinding
    String comment() default ""
}

@Singleton
@Colored(name = "red", shade = 3, dark = true, mode = Mode.STRIPED, type = String, tags = ["a", "b"], levels = [1, 2], types = [int[].class, Object[].class], detail = @Detail("fine"), comment = "ignored")
class Painted {
}

@Singleton
@Colored(name = "blank", tags = [], levels = [], details = [])
class Blank {
}

@Singleton
@Colored(name = "untyped", types = [])
class Untyped {
}

@Singleton
@Colored(name = "#{ 'dyn' + 'amic' }")
class Dynamic {
}

@Singleton
@Named("one")
class NamedOne implements Api {
}

@Singleton
@Requires(property = "descriptor.enabled", value = "true", defaultValue = "true")
@Requires(missingProperty = "descriptor.disabled")
@Requires(env = "test")
@Requires(notEnv = "cloud")
@Requires(classes = [String, int[].class])
@Requires(missingClasses = "test.Missing")
@Requires(entities = Marker)
@Requires(configuration = "test")
@Requires(sdk = Requires.Sdk.JAVA, version = "17")
@Requires(resources = "classpath:descriptor.txt")
@Requires(os = [Requires.Family.LINUX, Requires.Family.MAC_OS, Requires.Family.WINDOWS, Requires.Family.SOLARIS, Requires.Family.OTHER])
@Requires(notOs = Requires.Family.SOLARIS)
@Requires(beans = Plain)
@Requires(missingBeans = Eager)
class Conditional {
}

class Product {
}

@Factory
@Indexed(Other)
class Products {
    @Singleton
    @Named("first")
    Product first() {
        return new Product()
    }

    @Singleton
    Product[] all() {
        return new Product[0]
    }
}

class Library {
}

@Import(classes = Library)
class Imports {
}

@Around
@Retention(RetentionPolicy.RUNTIME)
@interface Traced {
}

@Singleton
@InterceptorBean(Traced)
class TracedInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed() + " traced"
    }
}

@Singleton
@Traced
class Advised {
    String hello() {
        return "hello"
    }
}
'''
}

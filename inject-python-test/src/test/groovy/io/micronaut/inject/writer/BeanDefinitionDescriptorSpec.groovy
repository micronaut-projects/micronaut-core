package io.micronaut.inject.writer

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import io.micronaut.context.DefaultBeanDefinitionsProvider
import io.micronaut.context.python.PythonContextRuntime
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.python.compiler.InMemoryBeanDefinitionsProvider
import io.micronaut.python.compiler.PyronautCompiler
import spock.lang.Shared
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_PROXIED_BEAN
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_PROXY_TARGET
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_SINGLETON

class BeanDefinitionDescriptorSpec extends AbstractBeanDefinitionDescriptorSpec {

    private static final String ENTRIES = "META-INF/micronaut/" + BeanDefinitionReference.name + "/"

    @Shared
    @TempDir
    private Path sources

    @Shared
    private URL[] classes

    void cleanupSpec() {
        PythonContextRuntime.resetContext()
    }

    @Override
    protected URL[] compile() {
        // the context feature runs Python, in a runtime of its own
        PythonContextRuntime.resetContext()
        // to a directory, as the Python compile of a build does: the comparison lists the entries, which it cannot do
        // in the memory of the in-memory compile
        Path directory = Files.createDirectories(sources.resolve('classes'))
        compiler().targetDir(directory.toFile()).build().compile()
        classes = [directory.toUri().toURL()] as URL[]
        return classes
    }

    private PyronautCompiler.Builder compiler() {
        Path python = sources.resolve('python')
        Path java = sources.resolve('java')
        if (!Files.exists(python)) {
            write(python.resolve('test/beans.py'), BEANS)
            JAVA.each { name, source -> write(java.resolve("test/${name}.java"), source) }
        }
        return PyronautCompiler.builder()
            .pythonSrc(python.toString())
            .javaSrc(java.toString())
    }

    private static void write(Path file, String content) {
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    @Override
    protected List<String> getModuleDefinitions() {
        // inject-python-test has no Python compiled for its class path, which test-suite-python has: these are the
        // beans of context-python, which a Python application needs and javac compiles
        return [
            'io.micronaut.context.python.$GraalPyContextFactory$Definition',
            'io.micronaut.context.python.$PythonPool$Definition'
        ]
    }

    @Override
    protected String getArrayBeanType() {
        // a Python annotation of a type names no array of a class: bytes is the one array it names
        return 'byte[]'
    }

    @Override
    protected List<String> getArrayExposedTypes() {
        return ['byte', 'byte[]']
    }

    @Override
    protected List<Integer> getAdvisedFlags() {
        // Python proxies the target of around advice at run time (@RuntimeProxy with proxyTarget, created by
        // PythonProxyCreator): the definition of the class is the proxy target, and the one of the proxy gives the
        // class itself
        return [FLAG_PROXIED_BEAN, FLAG_PROXY_TARGET, FLAG_SINGLETON]
    }

    @Override
    protected List<String> getAdviceProxy() {
        return ['Advised$RuntimeProxy', 'test.Advised']
    }

    @Override
    @PackageScope
    List<BeanDefinitionReference<?>> runtimeReferences(ClassLoader classLoader) {
        // and the beans that run Python, as InMemoryBeanDefinitionsProvider adds them
        return super.runtimeReferences(classLoader) + pythonReferences(classLoader)
    }

    @CompileStatic
    private static List<BeanDefinitionReference<?>> pythonReferences(ClassLoader classLoader) {
        // statically, since a reference of the class path may name a type the class path does not have
        return new DefaultBeanDefinitionsProvider().provide(classLoader).findAll { BeanDefinitionReference<?> reference ->
            reference.getClass().getName().startsWith('io.micronaut.context.python.')
        }
    }

    void "a definition built by a type element visitor has a descriptor"() {
        given: "the definition of the class that @Import makes a bean, which the processor of the visitors writes"
        def descriptor = descriptor('Imports$Library0')

        expect:
        descriptor.beanType() == 'test.Library'
        descriptor.exposedTypes() == ['test.Library']
    }

    void "the compile in memory writes the same entries as the compile to a directory"() {
        given: "the same sources compiled in memory, as the specs of this module and an application run from source are"
        URLClassLoader onDisk = new URLClassLoader(classes, (ClassLoader) null)
        Map<String, String> written = MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(onDisk)[BeanDefinitionReference.name].collectEntries(new TreeMap<>()) {
            [unnumbered(it), onDisk.getResource(ENTRIES + it).bytes.encodeHex().toString()]
        }
        ClassLoader inMemory = compiler().build().buildClassLoader()

        when: "its class loader gives every entry for the name of their directory, as InMemoryBeanDefinitionsProvider lists them"
        Map<String, String> writtenInMemory = Collections.list(inMemory.getResources(ENTRIES.substring(0, ENTRIES.length() - 1))).collectEntries(new TreeMap<>()) { URL entry ->
            String path = URLDecoder.decode(entry.path, 'UTF-8')
            [unnumbered(path.substring(path.lastIndexOf('/') + 1)), entry.bytes.encodeHex().toString()]
        }

        then: "the entries of the same definitions, each with the same content"
        written.size() > 15
        written.count { it.value.isEmpty() } == undescribed.size()
        writtenInMemory == written

        and: "the provider of that compile finds those definitions"
        new InMemoryBeanDefinitionsProvider(false).provide(inMemory)*.beanDefinitionName.collect { unnumbered(it) }.findAll { written.containsKey(it) } as Set == written.keySet()

        cleanup:
        onDisk?.close()
    }

    /**
     * A bean that {@code @Import} adds is numbered by a counter of the JVM, which a second compile of the same
     * sources does not start again: its definition has another number.
     */
    private static String unnumbered(String definition) {
        return definition.startsWith('test.$Imports$Library') ? 'test.$Imports$Library$Definition' : definition
    }

    private static final Map<String, String> JAVA = [
        Api        : '''
package test;

public interface Api {
}
''',
        Other      : '''
package test;

public interface Other {
}
''',
        Mode       : '''
package test;

public enum Mode {
    SOLID, STRIPED
}
''',
        Marker     : '''
package test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Marker {
}
''',
        Stereotyped: '''
package test;

import jakarta.inject.Singleton;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Marker
@Singleton
@Retention(RetentionPolicy.RUNTIME)
public @interface Stereotyped {
}
''',
        Draft      : '''
package test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
public @interface Draft {
}
''',
        Tag        : '''
package test;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Repeatable(Tags.class)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tag {
    String value();
}
''',
        Tags       : '''
package test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Tags {
    Tag[] value();
}
''',
        Detail     : '''
package test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Detail {
    String value();
}
''',
        Colored    : '''
package test;

import io.micronaut.context.annotation.NonBinding;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Qualifier;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface Colored {
    String name();

    int shade() default 0;

    boolean dark() default false;

    Mode mode() default Mode.SOLID;

    Class<?> type() default Object.class;

    String[] tags() default {};

    int[] levels() default {};

    Class<?>[] types() default {};

    Detail detail() default @Detail("none");

    Detail[] details() default {};

    Requires[] requirements() default {};

    @NonBinding
    String comment() default "";
}
''',
        Traced     : '''
package test;

import io.micronaut.aop.Around;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Around
@Retention(RetentionPolicy.RUNTIME)
public @interface Traced {
}
''',
        Library    : '''
package test;

public class Library {
}
'''
    ]

    // the beans of the specs of the other processors in Python. The annotations, whose members, repetition and
    // retention Python does not declare, the interfaces and the imported class are in Java (JAVA)
    private static final String BEANS = '''
import java

from jakarta.inject import Named, Singleton
from micronaut.aop import InterceptorBean, MethodInvocationContext
from micronaut.context.annotation import Bean, ConfigurationProperties, Context, Executable, Factory, Import, Parallel, Primary, Prototype, Requires
from micronaut.core.annotation import Indexed, Introspected
from test import Api, Colored, Detail, Draft, Library, Marker, Mode, Other, Stereotyped, Tag, Traced

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
# the class of an array, which a member of an annotation takes by the name java.type gives it
IntArray = java.type("int[]")
ObjectArray = java.type("java.lang.Object[]")


# a class whose bases are interfaces and that declares nothing compiles to an interface: these declare a method
@Singleton
class Plain(Api):
    def name(self) -> str:
        return "plain"


@Context
class Eager:
    pass


@Parallel
@Singleton
class InParallel:
    pass


@Primary
@Singleton
class First(Api):
    def name(self) -> str:
        return "first"


@Prototype
class Each:
    pass


@Singleton
@Bean(typed=[Api])
@Indexed(Other)
class Typed(Api, Other):
    def name(self) -> str:
        return "typed"


@ConfigurationProperties("descriptor.settings")
class Settings:
    name: str = None


@Singleton
class Startup:
    @Executable(processOnStartup=True)
    def run(self) -> None:
        pass


@Introspected
class Data:
    pass


@Stereotyped
@Draft
@Tag("a")
@Tag("b")
class Tagged:
    pass


@Singleton
@Colored(name="red", shade=3, dark=True, mode=Mode.STRIPED, type=str, tags=["a", "b"], levels=[1, 2], types=[IntArray, ObjectArray], detail=Detail("fine"), comment="ignored")
class Painted:
    pass


@Singleton
@Colored(name="blank", tags=[], levels=[], details=[])
class Blank:
    pass


@Singleton
@Colored(name="untyped", types=[])
class Untyped:
    pass


@Singleton
@Colored(name="unrequired", requirements=[])
class Unrequired:
    pass


@Singleton
@Colored(name="#{ 'dyn' + 'amic' }")
class Dynamic:
    pass


@Singleton
@Named("one")
class NamedOne(Api):
    def name(self) -> str:
        return "one"


@Singleton
@Requires(property="descriptor.enabled", value="true", defaultValue="true")
@Requires(missingProperty="descriptor.disabled")
@Requires(env=["test"])
@Requires(notEnv=["cloud"])
@Requires(classes=[str, IntArray])
@Requires(missingClasses=["test.Missing"])
@Requires(entities=[Marker])
@Requires(configuration="test")
@Requires(sdk=Requires.Sdk.JAVA, version="17")
@Requires(resources=["classpath:descriptor.txt"])
@Requires(os=[Requires.Family.LINUX, Requires.Family.MAC_OS, Requires.Family.WINDOWS, Requires.Family.SOLARIS, Requires.Family.OTHER])
@Requires(notOs=[Requires.Family.SOLARIS])
@Requires(beans=[Plain])
@Requires(missingBeans=[Eager])
class Conditional:
    pass


class Product:
    pass


@Factory
@Indexed(Other)
class Products:
    @Singleton
    @Named("first")
    def first(self) -> Product:
        return Product()

    @Singleton
    def all(self) -> bytes:
        return b""


@Import(classes=[Library])
class Imports:
    pass


@Singleton
@InterceptorBean(Traced)
class TracedInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        return context.proceed() + " traced"


@Singleton
@Traced
class Advised:
    def hello(self) -> str:
        return "hello"
'''
}

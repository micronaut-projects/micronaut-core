package io.micronaut.inject.writer

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import spock.lang.Shared
import spock.lang.TempDir

import javax.tools.JavaFileObject
import java.nio.file.Files
import java.nio.file.Path

class BeanDefinitionDescriptorSpec extends AbstractBeanDefinitionDescriptorSpec {

    private static final String CLASS_OUTPUT = '/CLASS_OUTPUT/'

    @Shared
    @TempDir
    Path classes

    @Override
    protected URL[] compile() {
        try (JavaParser parser = new JavaParser()) {
            for (JavaFileObject file : parser.generate('test.Advised', BEANS)) {
                if (file.name.startsWith(CLASS_OUTPUT)) {
                    Path target = classes.resolve(file.name.substring(CLASS_OUTPUT.length()))
                    Files.createDirectories(target.parent)
                    file.openInputStream().withCloseable { Files.copy(it, target) }
                }
            }
        }
        return [classes.toUri().toURL()] as URL[]
    }

    @Override
    protected List<String> getModuleDefinitions() {
        // compiled with the test classes of the module: a bean, a bean of a factory and a proxy
        return [
            'io.micronaut.inject.beans.$ParallelBean$Definition',
            'io.micronaut.inject.factory.multiple.$AFactory$A0$Definition',
            'io.micronaut.inject.beans.$InterceptedBean$Definition$Intercepted$Definition'
        ]
    }

    void "a definition built by a type element visitor has a descriptor"() {
        given: "the definition of the class that @Import makes a bean, which the processor of the visitors writes"
        def descriptor = descriptor('Imports$Library0')

        expect:
        descriptor.beanType() == 'test.Library'
        descriptor.exposedTypes() == ['test.Library']
    }

    void "the content a type element visitor gives the entry of a service is written"() {
        given: "a visitor that adds a service of its own with content"
        byte[] content = [1, 2, 3]
        TypeElementVisitor visitor = new TypeElementVisitor<Object, Object>() {
            @Override
            TypeElementVisitor.VisitorKind getVisitorKind() {
                return TypeElementVisitor.VisitorKind.ISOLATING
            }

            @Override
            void visitClass(ClassElement element, VisitorContext context) {
                if (element.simpleName == 'Visited') {
                    context.visitServiceDescriptor('test.Service', element.name, element, content)
                }
            }
        }
        JavaParser parser = new JavaParser() {
            @Override
            protected TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
                return new TypeElementVisitorProcessor() {
                    @Override
                    protected Collection<TypeElementVisitor> findTypeElementVisitors() {
                        return [visitor]
                    }
                }
            }
        }

        when:
        JavaFileObject entry = parser.generate('test.Visited', '''
package test;

@jakarta.inject.Singleton
class Visited {
}
''').find { it.name == CLASS_OUTPUT + 'META-INF/micronaut/test.Service/test.Visited' }

        then:
        entry != null
        entry.openInputStream().withCloseable { it.bytes } == content

        cleanup:
        parser.close()
    }

    private static final String BEANS = '''
package test;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Import;
import io.micronaut.context.annotation.NonBinding;
import io.micronaut.context.annotation.Parallel;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Named;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

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
@Bean(typed = Api.class)
@Indexed(Other.class)
class Typed implements Api, Other {
}

@ConfigurationProperties("descriptor.settings")
class Settings {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
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

@Repeatable(Tags.class)
@Retention(RetentionPolicy.RUNTIME)
@interface Tag {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@interface Tags {
    Tag[] value();
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
    String value();
}

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@interface Colored {
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

    @NonBinding
    String comment() default "";
}

@Singleton
@Colored(name = "red", shade = 3, dark = true, mode = Mode.STRIPED, type = String.class, tags = {"a", "b"}, levels = {1, 2}, types = {int[].class, Object[].class}, detail = @Detail("fine"), comment = "ignored")
class Painted {
}

@Singleton
@Colored(name = "blank", tags = {}, levels = {}, details = {})
class Blank {
}

@Singleton
@Colored(name = "untyped", types = {})
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
@Requires(classes = {String.class, int[].class})
@Requires(missingClasses = "test.Missing")
@Requires(entities = Marker.class)
@Requires(configuration = "test")
@Requires(sdk = Requires.Sdk.JAVA, version = "17")
@Requires(resources = "classpath:descriptor.txt")
@Requires(os = {Requires.Family.LINUX, Requires.Family.MAC_OS, Requires.Family.WINDOWS, Requires.Family.SOLARIS, Requires.Family.OTHER})
@Requires(notOs = Requires.Family.SOLARIS)
@Requires(beans = Plain.class)
@Requires(missingBeans = Eager.class)
class Conditional {
}

class Product {
}

@Factory
@Indexed(Other.class)
class Products {
    @Singleton
    @Named("first")
    Product first() {
        return new Product();
    }

    @Singleton
    Product[] all() {
        return new Product[0];
    }
}

class Library {
}

@Import(classes = Library.class)
class Imports {
}

@Around
@Retention(RetentionPolicy.RUNTIME)
@interface Traced {
}

@Singleton
@InterceptorBean(Traced.class)
class TracedInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed() + " traced";
    }
}

@Singleton
@Traced
public class Advised {
    public String hello() {
        return "hello";
    }
}
'''
}

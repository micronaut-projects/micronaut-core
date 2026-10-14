package io.micronaut.inject.descriptor

import io.micronaut.context.ApplicationContext
import io.micronaut.fixtures.context.MicronautApplicationTest
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.writer.BeanDefinitionDescriptors
import io.micronaut.runtime.Micronaut
import jakarta.validation.ConstraintViolationException

import java.nio.file.Files
import java.nio.file.Path

/**
 * An application compiled by this build, started as an application is, from its entries with descriptors, from the
 * same entries emptied as a release before descriptors writes them, and from half of them emptied.
 *
 * <p>Nothing reads the content of an entry yet, so the three give the same context by construction. It is what a
 * context that reads the descriptors has to keep giving: a definition without a usable descriptor is loaded as it
 * always was.</p>
 */
class BeanDefinitionDescriptorFallbackSpec extends MicronautApplicationTest {

    private static final String ENTRIES = "META-INF/micronaut/" + BeanDefinitionReference.name

    void "an application gives the same definitions and beans with its descriptors, without them and with half of them"() {
        given: "an application, in the Java the harness compiles"
        application()
        compile()
        Path classes = testDirectory.resolve('build/classes')
        List<Path> entries = Files.list(classes.resolve(ENTRIES)).withCloseable { it.sorted().toList() }

        expect: "every definition has a descriptor, which agrees with its class"
        entries.size() >= 10
        entries.every { Files.size(it) > 0 }
        with(BeanDefinitionDescriptors.compareAll(onlyFrom(classes))) {
            differences.isEmpty() && notLoaded.isEmpty() && withoutDescriptor.isEmpty() && compared.size() == entries.size()
        }

        when: "it is started from its entries, from a copy with every entry emptied, and from a copy with every other one emptied"
        Path emptied = copy(classes, testDirectory.resolve('emptied'), { true })
        Path half = copy(classes, testDirectory.resolve('half'), { Path entry -> entries.indexOf(classes.resolve(ENTRIES).resolve(entry.fileName.toString())) % 2 == 0 })
        Map<String, Object> described = snapshot(classes)
        Map<String, Object> undescribed = snapshot(emptied)
        Map<String, Object> mixed = snapshot(half)

        then: "the copies have the entries they are made for"
        contentSizes(emptied).every { it == 0 }
        contentSizes(half).count { it == 0 } == (entries.size() + 1).intdiv(2)
        contentSizes(half).count { it > 0 } == entries.size().intdiv(2)

        and: "the application has its beans"
        described.references.containsAll(['demo.$V6$Definition', 'demo.$Wheels$Front0$Definition', 'demo.$Registrar$Definition$Intercepted$Definition'])
        described.v6 == 'demo.V6'
        described.primary == 'demo.V8'
        described.engines == 2
        described.wheel == 'demo.Wheel'
        described.disabled == false
        described.greeting == 'hello logged'
        described.violation == ConstraintViolationException.name

        and: "the same without the descriptors and with half of them"
        undescribed == described
        mixed == described
    }

    /**
     * Starts the application from the given classes as {@link Micronaut} does, with the provider of the definitions
     * a context has by default, and answers the lookups the application is made for.
     */
    private Map<String, Object> snapshot(Path classes) {
        URLClassLoader loader = new URLClassLoader([classes.toUri().toURL()] as URL[], getClass().classLoader)
        ApplicationContext context = Micronaut.build().classLoader(loader).mainClass(loader.loadClass('demo.Application')).build().start()
        try {
            Class<?> engine = loader.loadClass('demo.Engine')
            Map<String, Object> answers = [
                references: context.beanDefinitionReferences*.beanDefinitionName.findAll { it.startsWith('demo.') }.sort(),
                v6        : context.getBean(engine, Qualifiers.byName('v6')).getClass().name,
                primary   : context.getBean(engine).getClass().name,
                engines   : context.getBean(loader.loadClass('demo.Engines')).count(),
                wheel     : context.getBean(loader.loadClass('demo.Wheel'), Qualifiers.byName('front')).getClass().name,
                disabled  : context.containsBean(loader.loadClass('demo.Disabled')),
                greeting  : context.getBean(loader.loadClass('demo.Greeter')).greet('hello')
            ]
            try {
                context.getBean(loader.loadClass('demo.Registrar')).register('')
                answers.violation = null
            } catch (ConstraintViolationException e) {
                answers.violation = e.getClass().name
            }
            return answers
        } finally {
            context.close()
            loader.close()
        }
    }

    private static List<Long> contentSizes(Path classes) {
        Files.list(classes.resolve(ENTRIES)).withCloseable { it.map { Files.size(it) }.toList() }
    }

    /**
     * Copies the classes with the entries the filter accepts emptied.
     */
    private static Path copy(Path classes, Path target, Closure<Boolean> emptied) {
        Files.walk(classes).withCloseable { paths ->
            paths.filter(Files::isRegularFile).forEach { Path file ->
                Path to = target.resolve(classes.relativize(file).toString())
                Files.createDirectories(to.parent)
                if (file.parent == classes.resolve(ENTRIES) && emptied(file)) {
                    Files.write(to, new byte[0])
                } else {
                    Files.copy(file, to)
                }
            }
        }
        return target
    }

    /**
     * @return A class loader that finds the resources of the given classes only
     */
    private URLClassLoader onlyFrom(Path classes) {
        URL[] urls = [classes.toUri().toURL()]
        new URLClassLoader(urls, getClass().classLoader) {
            @Override
            Enumeration<URL> getResources(String name) {
                return findResources(name)
            }
        }
    }

    private void application() {
        javaSourceFile('demo/Application.java', '''package demo;

public class Application {
}
''')
        javaSourceFile('demo/Engine.java', '''package demo;

public interface Engine {
    String name();
}
''')
        javaSourceFile('demo/V6.java', '''package demo;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("v6")
public class V6 implements Engine {
    @Override
    public String name() {
        return "v6";
    }
}
''')
        javaSourceFile('demo/V8.java', '''package demo;

import io.micronaut.context.annotation.Primary;
import jakarta.inject.Singleton;

@Primary
@Singleton
public class V8 implements Engine {
    @Override
    public String name() {
        return "v8";
    }
}
''')
        javaSourceFile('demo/Wheel.java', '''package demo;

public class Wheel {
}
''')
        javaSourceFile('demo/Wheels.java', '''package demo;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
public class Wheels {
    @Singleton
    @Named("front")
    public Wheel front() {
        return new Wheel();
    }
}
''')
        javaSourceFile('demo/Disabled.java', '''package demo;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "demo.disabled.enabled")
public class Disabled {
}
''')
        javaSourceFile('demo/Logged.java', '''package demo;

import io.micronaut.aop.Around;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Around
@Retention(RetentionPolicy.RUNTIME)
public @interface Logged {
}
''')
        javaSourceFile('demo/LoggedInterceptor.java', '''package demo;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import jakarta.inject.Singleton;

@Singleton
@InterceptorBean(Logged.class)
public class LoggedInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed() + " logged";
    }
}
''')
        javaSourceFile('demo/Greeter.java', '''package demo;

import jakarta.inject.Singleton;

@Singleton
@Logged
public class Greeter {
    public String greet(String greeting) {
        return greeting;
    }
}
''')
        javaSourceFile('demo/Engines.java', '''package demo;

import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class Engines {
    private final List<Engine> engines;

    public Engines(List<Engine> engines) {
        this.engines = engines;
    }

    public int count() {
        return engines.size();
    }
}
''')
        javaSourceFile('demo/Registrar.java', '''package demo;

import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;

@Singleton
public class Registrar {
    public String register(@NotBlank String name) {
        return name;
    }
}
''')
    }
}

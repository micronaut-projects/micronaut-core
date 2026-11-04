package io.micronaut.inject.failures

import groovy.transform.CompileStatic
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanInitializationFailure
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.inject.BeanDefinitionReference

import java.lang.reflect.Field

class DefinitionInitializationFailureSpec extends AbstractTypeElementSpec {

    void "a definition keeps a detached copy of the failure of its static initializer and throws it when loaded"() {
        given: "a bean with an injected type missing at runtime, as with an optional dependency"
        ClassLoader compiled = buildClassLoader('test.Foo', '''
package test;

@jakarta.inject.Singleton
class Foo {
    @jakarta.inject.Inject
    void setMissing(Missing missing) {
    }
}

class Missing {
}
''')
        ClassLoader loader = new WithoutMissing(compiled)

        when: "the definition is initialized"
        Class<?> definition = loader.loadClass('test.$Foo$Definition')
        Throwable failure = failureOf(definition)

        then: "the initialization did not fail the class, and what is kept holds no backtrace"
        failure instanceof BeanInitializationFailure
        failure.toString().startsWith(NoClassDefFoundError.name)
        failure.message == 'test/Missing'
        failure.stackTrace.length > 0

        when: "the definition is loaded"
        def context = ApplicationContext.builder().build()
        load(definition, context)

        then: "what it throws names the failure"
        def e = thrown(BeanInstantiationException)
        e.message.contains('Failed to initialize the bean')
        e.message.endsWith(': test/Missing')
        e.cause.is(failure)

        cleanup:
        context?.close()
    }

    // statically compiled: a dynamic call on the definition would introspect it and meet the missing class
    @CompileStatic
    private static Throwable failureOf(Class<?> definition) {
        Field field = definition.getDeclaredField('$FAILURE')
        field.setAccessible(true)
        return (Throwable) field.get(null)
    }

    @CompileStatic
    private static void load(Class<?> definition, ApplicationContext context) {
        BeanDefinitionReference reference = (BeanDefinitionReference) definition.getDeclaredConstructor().newInstance()
        reference.load(context)
    }

    /**
     * Defines the compiled classes itself, so that test.Missing can be missing for them.
     */
    private static final class WithoutMissing extends ClassLoader {
        private final ClassLoader compiled

        WithoutMissing(ClassLoader compiled) {
            super(DefinitionInitializationFailureSpec.classLoader)
            this.compiled = compiled
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name == 'test.Missing') {
                throw new ClassNotFoundException(name)
            }
            if (!name.startsWith('test.')) {
                return super.loadClass(name, resolve)
            }
            Class<?> loaded = findLoadedClass(name)
            if (loaded != null) {
                return loaded
            }
            List<URL> resources = compiled.getResources(name.replace('.', '/') + '.class').toList()
            if (resources.isEmpty()) {
                throw new ClassNotFoundException(name)
            }
            byte[] code = resources.first().openStream().withCloseable { it.readAllBytes() }
            return defineClass(name, code, 0, code.length)
        }
    }
}

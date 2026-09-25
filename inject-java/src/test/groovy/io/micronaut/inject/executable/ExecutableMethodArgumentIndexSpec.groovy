package io.micronaut.inject.executable

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.AbstractExecutableMethod
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod

class ExecutableMethodArgumentIndexSpec extends AbstractTypeElementSpec {

    void 'generated executable methods return arguments by name'() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('argget.MyBean', '''
package argget;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;

@Singleton
class MyBean {
    @Executable
    String greet(String name, int times) {
        return name.repeat(times);
    }
}
''')
        ExecutableMethod<?, ?> greet = definition.getRequiredMethod('greet', String, int)

        expect:
        greet.getArgument('name').get().is(greet.arguments[0])
        greet.getArgument('times').get().is(greet.arguments[1])
        greet.getArgument('times').get().type == int
        !greet.getArgument('missing').isPresent()

        when:
        greet.getArgument(null)

        then:
        thrown(NullPointerException)
    }

    void 'generated executable methods look up arguments by name'() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('argindex.MyBean', '''
package argindex;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;

@Singleton
class MyBean {
    @Executable
    String greet(String name, int times, String suffix) {
        return name.repeat(times) + suffix;
    }

    @Executable
    void run() {
    }
}
''')

        when:
        ExecutableMethod<?, ?> greet = definition.getRequiredMethod('greet', String, int, String)
        ExecutableMethod<?, ?> run = definition.getRequiredMethod('run')

        then:
        greet.argumentIndexOf('name') == 0
        greet.argumentIndexOf('times') == 1
        greet.argumentIndexOf('suffix') == 2
        greet.argumentIndexOf('missing') == -1
        greet.argumentIndexOf('times') == 1
        run.argumentIndexOf('name') == -1

        when:
        greet.argumentIndexOf(null)

        then:
        thrown(NullPointerException)
    }

    @SuppressWarnings('GrDeprecatedAPIUsage')
    void 'AbstractExecutableMethod subclasses look up arguments by name'() {
        given:
        Argument<?>[] methodArguments = (0..<10).collect { Argument.of(String, "a$it".toString()) } as Argument<?>[]
        def method = new AbstractExecutableMethod<Object, Object>(Object, 'm',
                Argument.OBJECT_ARGUMENT,
                methodArguments) {
            @Override
            protected Object invokeInternal(Object instance, Object[] arguments) { null }
        }

        expect:
        (0..<10).every { method.argumentIndexOf("a$it".toString()) == it }
        method.argumentIndexOf('missing') == -1
    }
}

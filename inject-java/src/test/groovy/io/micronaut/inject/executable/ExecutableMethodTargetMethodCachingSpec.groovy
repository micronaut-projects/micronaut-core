package io.micronaut.inject.executable

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod

import java.lang.reflect.Method

/**
 * {@code ExecutableMethod.getTargetMethod()} of a generated bean definition resolves the {@link Method} once.
 * {@link Class#getDeclaredMethod} hands out a fresh copy on every call, so identity across calls shows
 * that the executable method holds the resolved method rather than reflecting again.
 */
class ExecutableMethodTargetMethodCachingSpec extends AbstractTypeElementSpec {

    void 'test getTargetMethod returns the same Method instance on repeated calls'() {
        given:
        ApplicationContext context = buildContext('''
package targetmethod.caching;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;

@Singleton
class MyBean {
    @Executable
    String greet(String name, int times) {
        return name.repeat(times);
    }

    @Executable
    void run() {
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('targetmethod.caching.MyBean')
        BeanDefinition<?> definition = context.getBeanDefinition(beanType)

        expect: 'the JDK itself copies on every lookup, so identity below is not the JDK caching'
        !beanType.getDeclaredMethod('greet', String, int).is(beanType.getDeclaredMethod('greet', String, int))

        when:
        ExecutableMethod<?, ?> greet = definition.getRequiredMethod('greet', String, int)
        ExecutableMethod<?, ?> run = definition.getRequiredMethod('run')
        Method greetTarget = greet.targetMethod
        Method runTarget = run.targetMethod

        then:
        greetTarget == beanType.getDeclaredMethod('greet', String, int)
        runTarget == beanType.getDeclaredMethod('run')

        and: 'repeated calls return the held instance'
        greet.targetMethod.is(greetTarget)
        greet.targetMethod.is(greetTarget)
        run.targetMethod.is(runTarget)

        and: 'each executable method holds its own'
        !greetTarget.is(runTarget)
        definition.getRequiredMethod('greet', String, int).targetMethod.is(greetTarget)

        cleanup:
        context.close()
    }
}

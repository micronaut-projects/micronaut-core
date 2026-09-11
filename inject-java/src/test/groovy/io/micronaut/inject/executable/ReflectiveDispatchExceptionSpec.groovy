package io.micronaut.inject.executable

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.inject.BeanDefinition
import spock.lang.Unroll

/**
 * A private executable method is dispatched reflectively, a non-private one by generated code. Whichever it is, the
 * exception the method throws is the exception the caller of {@code ExecutableMethod.invoke} catches.
 */
class ReflectiveDispatchExceptionSpec extends AbstractTypeElementSpec {

    @Unroll
    void 'test a #visibility executable method throws what the method threw'() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('dispatch.direct.Throwing', """
package dispatch.direct;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.inject.Singleton;
import java.io.IOException;

@Singleton
class Throwing {

    @Executable
    @ReflectiveAccess
    $modifier String checked(String message) throws IOException {
        throw new IOException(message);
    }

    @Executable
    @ReflectiveAccess
    $modifier String unchecked(String message) {
        throw new IllegalStateException(message);
    }

    @Executable
    @ReflectiveAccess
    $modifier String error(String message) {
        throw new AssertionError(message);
    }
}
""")
        def constructor = definition.beanType.getDeclaredConstructor()
        constructor.accessible = true
        Object bean = constructor.newInstance()

        when:
        invoke(definition, 'checked', bean)

        then:
        IOException checked = thrown()
        checked.message == 'checked'

        when:
        invoke(definition, 'unchecked', bean)

        then:
        IllegalStateException unchecked = thrown()
        unchecked.message == 'unchecked'

        when:
        invoke(definition, 'error', bean)

        then:
        AssertionError error = thrown()
        error.message == 'error'

        where:
        visibility        | modifier
        'private'         | 'private'
        'package-private' | ''
    }

    void 'test a private executable method still fails when the reflective call itself fails'() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('dispatch.access.Target', '''
package dispatch.access;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.inject.Singleton;

@Singleton
class Target {

    @Executable
    @ReflectiveAccess
    private String hidden() {
        return "hidden";
    }
}
''')
        def method = definition.findMethod('hidden').get()

        when: 'the instance is not of the declaring type, which reflection rejects before the method runs'
        method.invoke('not the target')

        then:
        thrown(IllegalArgumentException)
    }

    @Unroll
    void 'test an interceptor dispatching through a #visibility executable method rethrows what the target threw'() {
        given:
        ApplicationContext context = buildContext("""
package dispatch.around;

import io.micronaut.aop.*;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Guarded {
}

@Singleton
@InterceptorBean(Guarded.class)
class GuardInterceptor implements MethodInterceptor<Object, Object> {
    static final List<Throwable> SEEN = new ArrayList<>();
    private final BeanContext beanContext;

    GuardInterceptor(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        try {
            return beanContext.getBeanDefinition(GuardInterceptor.class)
                .findMethod("guard", MethodInvocationContext.class)
                .orElseThrow()
                .invoke(this, context);
        } catch (Throwable e) {
            SEEN.add(e);
            throw e;
        }
    }

    @Executable
    @ReflectiveAccess
    $modifier Object guard(MethodInvocationContext<Object, Object> context) throws Exception {
        if (context.getMethodName().equals("io")) {
            throw new IOException("guard");
        }
        return context.proceed();
    }
}

@Singleton
@Guarded
class Service {
    public String io() {
        return "io";
    }

    public String fail() {
        throw new IllegalStateException("target");
    }
}
""")
        Class<?> interceptorType = context.classLoader.loadClass('dispatch.around.GuardInterceptor')
        Object service = context.getBean(context.classLoader.loadClass('dispatch.around.Service'))

        when: 'the private interceptor method throws a checked exception'
        service.io()

        then:
        IOException io = thrown()
        io.message == 'guard'
        interceptorType.SEEN[0].is(io)

        when: 'the target throws through the private interceptor method'
        service.fail()

        then:
        IllegalStateException failure = thrown()
        failure.message == 'target'
        interceptorType.SEEN[1].is(failure)

        cleanup:
        interceptorType.SEEN.clear()
        context.close()

        where:
        visibility        | modifier
        'private'         | 'private'
        'package-private' | ''
    }

    @Unroll
    void 'test a #visibility intercepted post construct callback reports the checked exception it threw'() {
        given:
        ApplicationContext context = buildContext("""
package dispatch.lifecycle;

import io.micronaut.aop.*;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static final List<Throwable> SEEN = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        try {
            return context.proceed();
        } catch (Throwable e) {
            SEEN.add(e);
            throw e;
        }
    }
}

@Singleton
@Tracked
class Failing {
    @PostConstruct
    $modifier void init() throws IOException {
        throw new IOException("init");
    }
}
""")
        Class<?> interceptorType = context.classLoader.loadClass('dispatch.lifecycle.TrackingInterceptor')

        when:
        context.getBean(context.classLoader.loadClass('dispatch.lifecycle.Failing'))

        then:
        thrown(BeanInstantiationException)
        interceptorType.SEEN.size() == 1
        interceptorType.SEEN[0] instanceof IOException
        interceptorType.SEEN[0].message == 'init'

        cleanup:
        interceptorType.SEEN.clear()
        context.close()

        where:
        visibility        | modifier
        'private'         | 'private'
        'package-private' | ''
    }

    private static Object invoke(BeanDefinition<?> definition, String name, Object bean) {
        return definition.findMethod(name, String).get().invoke(bean, name)
    }
}

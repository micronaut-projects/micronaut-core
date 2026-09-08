package io.micronaut.inject.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class PreDestroyOnBeanClassSpec extends AbstractTypeElementSpec {

    void "test preDestroy declared on the bean class"() {
        given:
        ApplicationContext context = buildContext('''\
package test;

import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
@Bean(preDestroy = "close")
class Test implements AutoCloseable {

    public boolean closed = false;

    @Override
    public void close() {
        closed = true;
    }
}
''')

        when:
        Class<?> beanType = context.classLoader.loadClass('test.Test')
        def bean = context.getBean(beanType)

        then:
        bean != null
        !bean.closed

        when:
        context.destroyBean(beanType)

        then:
        bean.closed
        bean != context.getBean(beanType)

        cleanup:
        context.close()
    }

    void "test preDestroy declared on the bean class is called when the context is closed"() {
        given:
        ApplicationContext context = buildContext('''\
package test;

import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
@Bean(preDestroy = "close")
class Test implements AutoCloseable {

    public boolean closed = false;

    @Override
    public void close() {
        closed = true;
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('test.Test')
        def bean = context.getBean(beanType)

        when:
        context.close()

        then:
        bean.closed
    }

    void "test preDestroy declared on the bean class resolving a method of a parent class"() {
        given:
        ApplicationContext context = buildContext('''\
package test;

import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
@Bean(preDestroy = "close")
class Test extends AbstractTest {}

class AbstractTest implements AutoCloseable {

    public boolean closed = false;

    @Override
    public void close() {
        closed = true;
    }
}
''')

        when:
        Class<?> beanType = context.classLoader.loadClass('test.Test')
        def bean = context.getBean(beanType)

        then:
        bean != null

        when:
        context.destroyBean(beanType)

        then:
        bean.closed

        cleanup:
        context.close()
    }

    void "test preDestroy declared on the bean class naming an overloaded method"() {
        given:
        ApplicationContext context = buildContext('''\
package test;

import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
@Bean(preDestroy = "close")
class Test {

    public boolean closed = false;

    public void close(Object context) {
        throw new RuntimeException("Should never have been called");
    }

    public void close() {
        closed = true;
    }
}
''')

        when:
        Class<?> beanType = context.classLoader.loadClass('test.Test')
        def bean = context.getBean(beanType)
        context.destroyBean(beanType)

        then:
        bean.closed

        cleanup:
        context.close()
    }

    void "test preDestroy declared on the bean class naming a method that is also annotated @PreDestroy is invoked once"() {
        given:
        ApplicationContext context = buildContext('''\
package test;

import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
@Bean(preDestroy = "close")
class Test {

    public int closedCount = 0;

    @jakarta.annotation.PreDestroy
    public void close() {
        closedCount++;
    }
}
''')

        when:
        Class<?> beanType = context.classLoader.loadClass('test.Test')
        def bean = context.getBean(beanType)
        context.destroyBean(beanType)

        then:
        bean.closedCount == 1

        cleanup:
        context.close()
    }

    void "test preDestroy declared on the bean class that does not exist"() {
        when:
        buildContext('''\
package test;

import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
@Bean(preDestroy = "notthere")
class Test {

    public void close() {
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("@Bean defines a preDestroy method that does not exist or is not public: notthere")
    }

    void "test preDestroy declared on the bean class naming a private method"() {
        when:
        buildContext('''\
package test;

import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
@Bean(preDestroy = "close")
class Test {

    private void close() {
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("@Bean defines a preDestroy method that does not exist or is not public: close")
    }

    void "test preDestroy declared on the bean class naming a method with parameters"() {
        when:
        buildContext('''\
package test;

import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
@Bean(preDestroy = "close")
class Test {

    public void close(Object context) {
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("@Bean defines a preDestroy method that does not exist or is not public: close")
    }

    void "test preDestroy declared on an AOP advised bean class is not advised"() {
        given:
        ApplicationContext context = buildContext('''\
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.aop.*;
import java.lang.annotation.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.*;

@Retention(RUNTIME)
@Target({TYPE, METHOD})
@Around
@interface Mutating {
}

@InterceptorBean(Mutating.class)
class MutatingInterceptor implements MethodInterceptor<Object, Object> {
    public static int invocations = 0;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        invocations++;
        return context.proceed();
    }
}

@jakarta.inject.Singleton
@Mutating
@Bean(preDestroy = "close")
class Test {

    public boolean closed = false;

    public void doStuff() {
    }

    public void close() {
        closed = true;
    }
}
''')

        when:
        Class<?> beanType = context.classLoader.loadClass('test.Test')
        Class<?> interceptorType = context.classLoader.loadClass('test.MutatingInterceptor')
        def bean = context.getBean(beanType)
        bean.doStuff()
        int invocationsAfterDoStuff = interceptorType.invocations

        then:
        invocationsAfterDoStuff > 0

        when:
        context.destroyBean(beanType)

        then: "the callback ran but was not advised"
        interceptorType.invocations == invocationsAfterDoStuff

        cleanup:
        context.close()
    }
}

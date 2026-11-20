package io.micronaut.inject.dependencies

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class BeanDependencyGroupAdvisedSpec extends AbstractTypeElementSpec {

    void "fresh registrations of an around-advised singleton created through a group are released with that group"() {
        given:
        def ctx = buildContext('''
package test;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.*;

class Log { static final List<String> events = new CopyOnWriteArrayList<>(); }

@Retention(RetentionPolicy.RUNTIME)
@Around
@interface Traced {
}

@Singleton
@InterceptorBean(Traced.class)
class TracedInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@Singleton
@Traced
class Owner {
    public String hello() { return "hello"; }
    @PreDestroy void stop() { Log.events.add("owner"); }
}
''')
        def type = ctx.classLoader.loadClass('test.Owner')
        def log = ctx.classLoader.loadClass('test.Log')
        def group = ctx.createDependencyGroup()
        def first = group.createBeanRegistration(ctx.getBeanDefinition(type))
        def second = group.createBeanRegistration(ctx.getBeanDefinition(type))

        expect:
        first.bean.hello() == 'hello'
        !first.bean.is(second.bean)

        when:
        group.destroy(first)

        then:
        log.events == ['owner']

        when:
        group.close()

        then:
        log.events == ['owner', 'owner']

        when:
        second.close()

        then:
        log.events == ['owner', 'owner']

        cleanup:
        ctx.close()
    }
}

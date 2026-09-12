package io.micronaut.aop.constructor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import io.micronaut.core.beans.BeanIntrospection

/**
 * {@code BeanConstructor.getTargetConstructor()} resolves the {@link java.lang.reflect.Constructor} a
 * constructor interceptor is advising, the way {@code MethodReference.getTargetMethod()} does for a method.
 */
class AroundConstructTargetConstructorSpec extends AbstractTypeElementSpec {

    void 'test the target constructor of a plain around construct bean is its declared constructor'() {
        given:
        ApplicationContext context = buildContext('''
package targetctor.plain;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.lang.reflect.Constructor;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@AroundConstruct
@interface Tracked {
}

@Singleton
class Alpha {
}

@Singleton
@Tracked
class MyBean {
    final Alpha alpha;

    MyBean(Alpha alpha) {
        this.alpha = alpha;
    }
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND_CONSTRUCT)
class CapturingInterceptor implements ConstructorInterceptor<Object> {
    static Constructor<?> first;
    static Constructor<?> second;

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        first = context.getConstructor().getTargetConstructor();
        second = context.getConstructor().getTargetConstructor();
        return context.proceed();
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('targetctor.plain.MyBean')
        Class<?> alphaType = context.classLoader.loadClass('targetctor.plain.Alpha')
        Class<?> interceptorType = context.classLoader.loadClass('targetctor.plain.CapturingInterceptor')

        when:
        def bean = context.getBean(beanType)

        then:
        !(bean instanceof Intercepted)
        interceptorType.first == beanType.getDeclaredConstructor(alphaType)
        interceptorType.first.declaringClass == beanType

        and: 'the constructor is resolved once'
        interceptorType.second.is(interceptorType.first)

        cleanup:
        context.close()
    }

    void 'test the target constructor of an around advised bean is the target class constructor with the declared parameters'() {
        given:
        ApplicationContext context = buildContext('''
package targetctor.proxy;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.lang.reflect.Constructor;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Around
@AroundConstruct
@interface Tracked {
}

@Singleton
class Alpha {
}

@Singleton
class Beta {
}

@Singleton
@Tracked
class MyBean {
    final Alpha alpha;
    final Beta beta;

    MyBean(Alpha alpha, Beta beta) {
        this.alpha = alpha;
        this.beta = beta;
    }

    String work() {
        return "done";
    }
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND_CONSTRUCT)
class CapturingInterceptor implements ConstructorInterceptor<Object> {
    static Constructor<?> first;
    static Constructor<?> second;
    static int arguments = -1;

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        first = context.getConstructor().getTargetConstructor();
        second = context.getConstructor().getTargetConstructor();
        arguments = context.getConstructor().getArguments().length;
        return context.proceed();
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('targetctor.proxy.MyBean')
        Class<?> alphaType = context.classLoader.loadClass('targetctor.proxy.Alpha')
        Class<?> betaType = context.classLoader.loadClass('targetctor.proxy.Beta')
        Class<?> interceptorType = context.classLoader.loadClass('targetctor.proxy.CapturingInterceptor')

        when:
        def bean = context.getBean(beanType)

        then: 'the bean is a proxy, constructed through a proxy constructor with extra trailing parameters'
        bean instanceof Intercepted
        bean.work() == 'done'
        bean.getClass() != beanType
        bean.getClass().getDeclaredConstructors().every { it.parameterCount > 2 }

        and: 'the target constructor is the one the intercepted class declares, not the proxy constructor'
        interceptorType.arguments == 2
        interceptorType.first == beanType.getDeclaredConstructor(alphaType, betaType)
        interceptorType.first.declaringClass == beanType
        interceptorType.first.parameterCount == 2

        and: 'the constructor is resolved once'
        interceptorType.second.is(interceptorType.first)

        cleanup:
        context.close()
    }

    void 'test the target constructor of a factory produced bean is null'() {
        given:
        ApplicationContext context = buildContext('''
package targetctor.factory;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.lang.reflect.Constructor;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@AroundConstruct
@interface Tracked {
}

@Singleton
class Alpha {
}

class MyBean {
    final Alpha alpha;

    MyBean(Alpha alpha) {
        this.alpha = alpha;
    }
}

@Factory
class MyBeanFactory {
    @Bean
    @Singleton
    @Tracked
    MyBean myBean(Alpha alpha) {
        return new MyBean(alpha);
    }
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND_CONSTRUCT)
class CapturingInterceptor implements ConstructorInterceptor<Object> {
    static int intercepted;
    static Class<?> declaringBeanType;
    static Constructor<?> target;
    static boolean resolved;

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        intercepted++;
        declaringBeanType = context.getConstructor().getDeclaringBeanType();
        target = context.getConstructor().getTargetConstructor();
        resolved = true;
        return context.proceed();
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('targetctor.factory.MyBean')
        Class<?> interceptorType = context.classLoader.loadClass('targetctor.factory.CapturingInterceptor')

        when:
        def bean = context.getBean(beanType)

        then:
        bean != null
        interceptorType.intercepted == 1
        interceptorType.resolved

        and: 'the factory method, not a constructor of the bean type, creates the bean, so there is no target constructor even though MyBean declares a constructor with the same parameter types'
        interceptorType.declaringBeanType == beanType
        beanType.getDeclaredConstructor(context.classLoader.loadClass('targetctor.factory.Alpha')) != null
        interceptorType.target == null

        cleanup:
        context.close()
    }

    void 'test the target constructor of an introspection is its declared constructor'() {
        given:
        BeanIntrospection<?> introspection = buildBeanIntrospection('targetctor.introspection.MyBean', '''
package targetctor.introspection;

import io.micronaut.core.annotation.Introspected;

@Introspected
class MyBean {
    final String name;
    final int count;

    MyBean(String name, int count) {
        this.name = name;
        this.count = count;
    }

    String getName() {
        return name;
    }

    int getCount() {
        return count;
    }
}
''')

        when:
        def constructor = introspection.getConstructor()

        then:
        constructor.getTargetConstructor() == introspection.beanType.getDeclaredConstructor(String, int)
        constructor.getTargetConstructor().is(constructor.getTargetConstructor())
    }
}

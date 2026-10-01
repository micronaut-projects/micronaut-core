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

    void 'test the target constructor of a factory produced around advised bean is null'() {
        given:
        ApplicationContext context = buildContext('''
package targetctor.factoryproxy;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.lang.reflect.Constructor;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@AroundConstruct
@interface Tracked {
}

@Singleton
class Alpha {
}

class MyBean {
    final Alpha alpha;

    MyBean() {
        this(null);
    }

    MyBean(Alpha alpha) {
        this.alpha = alpha;
    }

    String work() {
        return "done";
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
    static List<Class<?>> declaringBeanTypes = new ArrayList<>();
    static List<Constructor<?>> targets = new ArrayList<>();
    static List<Integer> argumentCounts = new ArrayList<>();

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        declaringBeanTypes.add(context.getConstructor().getDeclaringBeanType());
        targets.add(context.getConstructor().getTargetConstructor());
        argumentCounts.add(context.getConstructor().getArguments().length);
        return context.proceed();
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('targetctor.factoryproxy.MyBean')
        Class<?> interceptorType = context.classLoader.loadClass('targetctor.factoryproxy.CapturingInterceptor')

        when:
        def bean = context.getBean(beanType)

        then:
        bean instanceof Intercepted
        bean.work() == 'done'

        and: 'every interception describes the bean type, and none of them names a constructor, since the factory made the instance'
        !interceptorType.declaringBeanTypes.isEmpty()
        interceptorType.declaringBeanTypes.every { it == beanType }
        interceptorType.targets.every { it == null }
        beanType.getDeclaredConstructor() != null

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

    void 'test the target constructor of an introspection instantiating through a static creator is null'() {
        given: 'a static creator and a constructor with the same parameter types'
        BeanIntrospection<?> introspection = buildBeanIntrospection('targetctor.creator.MyBean', '''
package targetctor.creator;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;

@Introspected
class MyBean {
    final String name;

    MyBean(String name) {
        this.name = name;
    }

    @Creator
    static MyBean create(String name) {
        return new MyBean(name + "!");
    }

    String getName() {
        return name;
    }
}
''')

        when:
        def constructor = introspection.getConstructor()

        then: 'the creator instantiates the bean'
        introspection.instantiate('a').name == 'a!'
        constructor.arguments*.type == [String]
        introspection.beanType.getDeclaredConstructor(String) != null

        and: 'so the constructor with the same signature is not the target'
        constructor.getTargetConstructor() == null
        constructor.getTargetConstructor() == null
    }

    void 'test the target constructors of an introspection describing all constructors'() {
        given:
        BeanIntrospection<?> introspection = buildBeanIntrospection('targetctor.declared.MyBean', '''
package targetctor.declared;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;

@Introspected(constructors = true)
class MyBean {
    final String name;
    final int count;

    MyBean() {
        this("", 0);
    }

    MyBean(String name, int count) {
        this.name = name;
        this.count = count;
    }

    @Creator
    static MyBean create(String name, int count) {
        return new MyBean(name, count);
    }

    String getName() {
        return name;
    }

    int getCount() {
        return count;
    }
}
''')
        Class<?> beanType = introspection.beanType

        when:
        def constructors = introspection.getConstructors()

        then: 'the instantiating creator first, then the declared constructors'
        constructors.size() == 3
        constructors[0].arguments*.type == [String, int]
        constructors[0].getTargetConstructor() == null
        constructors[1].getTargetConstructor() == beanType.getDeclaredConstructor()
        constructors[2].getTargetConstructor() == beanType.getDeclaredConstructor(String, int)
        constructors[2].getTargetConstructor().is(constructors[2].getTargetConstructor())

        and: 'the declared constructors instantiate through what they describe'
        constructors[2].instantiate('x', 2).count == 2
    }

    void 'test the target constructor of an enum introspection is null'() {
        given:
        BeanIntrospection<?> introspection = buildBeanIntrospection('targetctor.enums.Colour', '''
package targetctor.enums;

import io.micronaut.core.annotation.Introspected;

@Introspected
enum Colour {
    RED, GREEN
}
''')

        expect:
        introspection.getConstructor().getTargetConstructor() == null
    }

    void 'test the target constructor of a bean with parameters is its declared constructor'() {
        given:
        ApplicationContext context = buildContext('''
package targetctor.parametrized;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
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

@Prototype
@Tracked
class Product {
    final String name;
    final Alpha alpha;

    Product(@Parameter String name, Alpha alpha) {
        this.name = name;
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
        Class<?> beanType = context.classLoader.loadClass('targetctor.parametrized.Product')
        Class<?> alphaType = context.classLoader.loadClass('targetctor.parametrized.Alpha')
        Class<?> interceptorType = context.classLoader.loadClass('targetctor.parametrized.CapturingInterceptor')

        when:
        def bean = context.createBean(beanType, [name: 'widget'])

        then:
        bean.name == 'widget'
        interceptorType.first == beanType.getDeclaredConstructor(String, alphaType)
        interceptorType.second.is(interceptorType.first)

        cleanup:
        context.close()
    }
}

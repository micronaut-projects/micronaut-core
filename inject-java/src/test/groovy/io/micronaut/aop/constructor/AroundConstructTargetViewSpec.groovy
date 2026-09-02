package io.micronaut.aop.constructor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext

class AroundConstructTargetViewSpec extends AbstractTypeElementSpec {

    void 'test a constructor interceptor of an around advised bean sees the target constructor'() {
        given:
        ApplicationContext context = buildContext('''
package ctorview.proxy;

import io.micronaut.aop.*;
import io.micronaut.core.type.Argument;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

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
    static Class<?> declaringType;
    static Class<?> constructorDeclaringType;
    static List<String> argumentNames;
    static List<Class<?>> argumentTypes;
    static Object[] parameterValues;
    static String description;

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        declaringType = context.getDeclaringType();
        constructorDeclaringType = context.getConstructor().getDeclaringBeanType();
        argumentNames = Arrays.stream(context.getArguments()).map(Argument::getName).toList();
        argumentTypes = Arrays.stream(context.getArguments()).<Class<?>>map(Argument::getType).toList();
        parameterValues = context.getParameterValues();
        description = context.getConstructor().getDescription();
        return context.proceed();
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('ctorview.proxy.MyBean')
        Class<?> alphaType = context.classLoader.loadClass('ctorview.proxy.Alpha')
        Class<?> betaType = context.classLoader.loadClass('ctorview.proxy.Beta')
        Class<?> interceptorType = context.classLoader.loadClass('ctorview.proxy.CapturingInterceptor')

        when:
        def bean = context.getBean(beanType)

        then: 'the bean is an around proxy that was constructed through the interceptor'
        bean instanceof Intercepted
        bean.work() == 'done'
        bean.alpha.is(context.getBean(alphaType))
        bean.beta.is(context.getBean(betaType))

        and: 'the context describes the target constructor, not the generated proxy constructor'
        interceptorType.declaringType == beanType
        interceptorType.constructorDeclaringType == beanType
        interceptorType.argumentNames == ['alpha', 'beta']
        interceptorType.argumentTypes == [alphaType, betaType]
        interceptorType.description == 'MyBean(Alpha alpha,Beta beta)'

        and: 'the arguments and the parameter values line up'
        interceptorType.parameterValues.length == interceptorType.argumentNames.size()
        interceptorType.parameterValues[0].is(context.getBean(alphaType))
        interceptorType.parameterValues[1].is(context.getBean(betaType))

        cleanup:
        context.close()
    }

    void 'test the target constructor view of an around advised bean can instantiate the bean'() {
        given:
        ApplicationContext context = buildContext('''
package ctorview.instantiate;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

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
@Tracked
class MyBean {
    final Alpha alpha;

    MyBean(Alpha alpha) {
        this.alpha = alpha;
    }

    String work() {
        return "done";
    }
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND_CONSTRUCT)
class InstantiatingInterceptor implements ConstructorInterceptor<Object> {
    static int arguments = -1;

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        arguments = context.getArguments().length;
        // Bypass proceed() and construct through the constructor the context exposes
        return context.getConstructor().instantiate(context.getParameterValues());
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('ctorview.instantiate.MyBean')
        Class<?> interceptorType = context.classLoader.loadClass('ctorview.instantiate.InstantiatingInterceptor')

        when:
        def bean = context.getBean(beanType)

        then:
        bean instanceof Intercepted
        bean.work() == 'done'
        bean.alpha.is(context.getBean(context.classLoader.loadClass('ctorview.instantiate.Alpha')))
        interceptorType.arguments == 1

        cleanup:
        context.close()
    }

    void 'test a constructor interceptor of an around advised bean without parameters sees no arguments'() {
        given:
        ApplicationContext context = buildContext('''
package ctorview.noargs;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Around
@AroundConstruct
@interface Tracked {
}

@Singleton
@Tracked
class MyBean {
    String work() {
        return "done";
    }
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND_CONSTRUCT)
class CapturingInterceptor implements ConstructorInterceptor<Object> {
    static Class<?> declaringType;
    static int arguments = -1;
    static int parameterValues = -1;

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        declaringType = context.getDeclaringType();
        arguments = context.getArguments().length;
        parameterValues = context.getParameterValues().length;
        return context.proceed();
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('ctorview.noargs.MyBean')
        Class<?> interceptorType = context.classLoader.loadClass('ctorview.noargs.CapturingInterceptor')

        when:
        def bean = context.getBean(beanType)

        then:
        bean instanceof Intercepted
        bean.work() == 'done'
        interceptorType.declaringType == beanType
        interceptorType.arguments == 0
        interceptorType.parameterValues == 0

        cleanup:
        context.close()
    }

    void 'test a constructor interceptor of a plain around construct bean is unchanged'() {
        given:
        ApplicationContext context = buildContext('''
package ctorview.plain;

import io.micronaut.aop.*;
import io.micronaut.core.type.Argument;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
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
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND_CONSTRUCT)
class CapturingInterceptor implements ConstructorInterceptor<Object> {
    static Class<?> declaringType;
    static List<String> argumentNames;
    static Object[] parameterValues;

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        declaringType = context.getDeclaringType();
        argumentNames = Arrays.stream(context.getArguments()).map(Argument::getName).toList();
        parameterValues = context.getParameterValues();
        return context.proceed();
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('ctorview.plain.MyBean')
        Class<?> alphaType = context.classLoader.loadClass('ctorview.plain.Alpha')
        Class<?> betaType = context.classLoader.loadClass('ctorview.plain.Beta')
        Class<?> interceptorType = context.classLoader.loadClass('ctorview.plain.CapturingInterceptor')

        when:
        def bean = context.getBean(beanType)

        then: 'the bean is not proxied and the context already described the target constructor'
        !(bean instanceof Intercepted)
        bean.alpha.is(context.getBean(alphaType))
        bean.beta.is(context.getBean(betaType))
        interceptorType.declaringType == beanType
        interceptorType.argumentNames == ['alpha', 'beta']
        interceptorType.parameterValues.length == 2
        interceptorType.parameterValues[0].is(context.getBean(alphaType))
        interceptorType.parameterValues[1].is(context.getBean(betaType))

        cleanup:
        context.close()
    }
}

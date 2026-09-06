package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * The pre-destroy method a {@code @Factory} method names with {@link io.micronaut.context.annotation.Bean#preDestroy()}
 * has to be invoked whether or not the produced bean is proxied: around advice on the producing method makes the bean
 * a proxy target, and the callback belongs to the target, not to the proxy.
 *
 * <p>{@link LifecycleCallbackStatesSpec} covers the callback itself; what is covered here is that proxying does not
 * lose it, either silently or by making it unreachable as an executable method.</p>
 */
class ProxiedFactoryPreDestroySpec extends AbstractTypeElementSpec {

    void 'test the pre destroy method of an unproxied factory produced bean is invoked'() {
        given:
        ApplicationContext context = buildContext(source('unproxied', '', ''))
        Class<?> callsType = context.classLoader.loadClass('proxiedfactory.unproxied.Calls')
        Class<?> disposableType = context.classLoader.loadClass('proxiedfactory.unproxied.Disposable')

        when:
        def disposable = context.getBean(disposableType)
        callsType.RECORDED.clear()
        disposable.use()
        context.destroyBean(disposable)

        then:
        callsType.RECORDED == ['use', 'close']

        cleanup:
        context.close()
    }

    void 'test the pre destroy method of a proxied factory produced bean is invoked'() {
        given:
        ApplicationContext context = buildContext(source('proxied', AROUND_ADVICE, '@Traced'))
        Class<?> callsType = context.classLoader.loadClass('proxiedfactory.proxied.Calls')
        Class<?> disposableType = context.classLoader.loadClass('proxiedfactory.proxied.Disposable')

        when:
        def disposable = context.getBean(disposableType)
        callsType.RECORDED.clear()
        disposable.use()
        context.destroyBean(disposable)

        then:
        callsType.RECORDED == ['AROUND:use', 'use', 'close']

        cleanup:
        context.close()
    }

    void 'test the pre destroy method of a proxied factory produced bean is an intercepted callback of the target'() {
        given:
        ApplicationContext context = buildContext(source('bound', AROUND_AND_LIFECYCLE_ADVICE, '@Traced'))
        Class<?> callsType = context.classLoader.loadClass('proxiedfactory.bound.Calls')
        Class<?> disposableType = context.classLoader.loadClass('proxiedfactory.bound.Disposable')

        when:
        def disposable = context.getBean(disposableType)
        callsType.RECORDED.clear()
        disposable.use()
        context.destroyBean(disposable)

        then: 'the callback is dispatched by the target definition, so the pre-destroy advice sees it'
        callsType.RECORDED == ['AROUND:use', 'use', 'PRE_DESTROY:close', 'close']

        cleanup:
        context.close()
    }

    void 'test the pre destroy method of a proxied singleton is invoked when the context is closed'() {
        given:
        ApplicationContext context = buildContext('''
package proxiedfactory.singleton;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

class Calls {
    static final List<String> RECORDED = new ArrayList<>();
}

class Disposable {
    public String use() { Calls.RECORDED.add("use"); return "used"; }
    public void close() { Calls.RECORDED.add("close"); }
}

@Around
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface Traced {
}

@InterceptorBean(Traced.class)
class TracingInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Calls.RECORDED.add(context.getKind() + ":" + context.getExecutableMethod().getMethodName());
        return context.proceed();
    }
}

@Factory
class DisposableFactory {
    @Bean(preDestroy = "close")
    @Singleton
    @Traced
    Disposable disposable() { return new Disposable(); }
}
''')
        Class<?> callsType = context.classLoader.loadClass('proxiedfactory.singleton.Calls')
        Class<?> disposableType = context.classLoader.loadClass('proxiedfactory.singleton.Disposable')

        when:
        def disposable = context.getBean(disposableType)
        callsType.RECORDED.clear()
        disposable.use()
        context.stop()

        then:
        callsType.RECORDED == ['AROUND:use', 'use', 'close']

        cleanup:
        context.close()
    }

    void 'test proxying does not change which pre destroy callbacks a factory produced bean runs'() {
        given: 'the produced type declares a @PreDestroy callback of its own alongside the one the factory names'
        ApplicationContext context = buildContext(bothCallbacksSource(proxied ? 'proxiedboth' : 'plainboth', proxied))
        Class<?> callsType = context.classLoader.loadClass("proxiedfactory.${proxied ? 'proxiedboth' : 'plainboth'}.Calls")
        Class<?> disposableType = context.classLoader.loadClass("proxiedfactory.${proxied ? 'proxiedboth' : 'plainboth'}.Disposable")

        when:
        def disposable = context.getBean(disposableType)
        callsType.RECORDED.clear()
        context.destroyBean(disposable)

        then: 'the produced type is not a declared bean, so only the callback the factory names runs, proxied or not'
        callsType.RECORDED == ['close']

        cleanup:
        context.close()

        where:
        proxied << [false, true]
    }

    void 'test the pre destroy method of a proxied factory produced bean is resolved from a super class'() {
        given:
        ApplicationContext context = buildContext('''
package proxiedfactory.inherited;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import java.lang.annotation.*;
import java.util.*;

class Calls {
    static final List<String> RECORDED = new ArrayList<>();
}

class AbstractDisposable {
    public void close() { Calls.RECORDED.add("close"); }
}

class Disposable extends AbstractDisposable {
    public String use() { Calls.RECORDED.add("use"); return "used"; }
}

@Around
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface Traced {
}

@InterceptorBean(Traced.class)
class TracingInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Calls.RECORDED.add(context.getKind() + ":" + context.getExecutableMethod().getMethodName());
        return context.proceed();
    }
}

@Factory
class DisposableFactory {
    @Bean(preDestroy = "close")
    @Prototype
    @Traced
    Disposable disposable() { return new Disposable(); }
}
''')
        Class<?> callsType = context.classLoader.loadClass('proxiedfactory.inherited.Calls')
        Class<?> disposableType = context.classLoader.loadClass('proxiedfactory.inherited.Disposable')

        when:
        def disposable = context.getBean(disposableType)
        callsType.RECORDED.clear()
        disposable.use()
        context.destroyBean(disposable)

        then:
        callsType.RECORDED == ['AROUND:use', 'use', 'close']

        cleanup:
        context.close()
    }

    private static final String AROUND_ADVICE = '''
@Around
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface Traced {
}

@InterceptorBean(Traced.class)
class TracingInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Calls.RECORDED.add(context.getKind() + ":" + context.getExecutableMethod().getMethodName());
        return context.proceed();
    }
}
'''

    private static final String AROUND_AND_LIFECYCLE_ADVICE = '''
@Around
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface Traced {
}

@jakarta.inject.Singleton
@InterceptorBinding(value = Traced.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Traced.class, kind = InterceptorKind.PRE_DESTROY)
class TracingInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Calls.RECORDED.add(context.getKind() + ":" + context.getExecutableMethod().getMethodName());
        return context.proceed();
    }
}
'''

    void 'test an inaccessible pre destroy method is rejected the same way whether or not the bean is proxied'() {
        when:
        buildContext(privateCallbackSource(proxied ? 'proxiedprivate' : 'plainprivate', proxied))

        then:
        RuntimeException e = thrown()
        e.message.contains('@Bean method defines a preDestroy method that does not exist or is not public: close')

        where:
        proxied << [false, true]
    }

    private static String privateCallbackSource(String pkg, boolean proxied) {
        """
package proxiedfactory.$pkg;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import java.lang.annotation.*;
import java.util.*;

class Calls {
    static final List<String> RECORDED = new ArrayList<>();
}

class Disposable {
    public String use() { Calls.RECORDED.add("use"); return "used"; }
    private void close() { Calls.RECORDED.add("close"); }
}
${proxied ? AROUND_ADVICE : ''}
@Factory
class DisposableFactory {
    @Bean(preDestroy = "close")
    @Prototype
    ${proxied ? '@Traced' : ''}
    Disposable disposable() { return new Disposable(); }
}
"""
    }

    private static String bothCallbacksSource(String pkg, boolean proxied) {
        """
package proxiedfactory.$pkg;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.annotation.PreDestroy;
import java.lang.annotation.*;
import java.util.*;

class Calls {
    static final List<String> RECORDED = new ArrayList<>();
}

class Disposable {
    @PreDestroy
    public void shutdown() { Calls.RECORDED.add("shutdown"); }

    public void close() { Calls.RECORDED.add("close"); }
}
${proxied ? AROUND_ADVICE : ''}
@Factory
class DisposableFactory {
    @Bean(preDestroy = "close")
    @Prototype
    ${proxied ? '@Traced' : ''}
    Disposable disposable() { return new Disposable(); }
}
"""
    }

    private static String source(String pkg, String advice, String adviceAnnotation) {
        """
package proxiedfactory.$pkg;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import java.lang.annotation.*;
import java.util.*;

class Calls {
    static final List<String> RECORDED = new ArrayList<>();
}

class Disposable {
    public String use() { Calls.RECORDED.add("use"); return "used"; }
    public void close() { Calls.RECORDED.add("close"); }
}
$advice
@Factory
class DisposableFactory {
    @Bean(preDestroy = "close")
    @Prototype
    $adviceAnnotation
    Disposable disposable() { return new Disposable(); }
}
"""
    }
}

package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.InterceptorRegistry
import io.micronaut.aop.HotSwappableInterceptedProxy
import io.micronaut.inject.qualifiers.Qualifiers

class MethodChainFactorySpec extends AbstractTypeElementSpec {
    private final String infrastructure = '''
package method.factory;
import io.micronaut.aop.*;
import io.micronaut.aop.chain.*;
import io.micronaut.context.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import java.lang.reflect.Method;
import io.micronaut.inject.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Singleton @Bean(typed = InterceptorRegistry.class) @Replaces(InterceptorRegistry.class)
class CountingRegistry implements InterceptorRegistry {
    int selections;
    final InterceptorRegistry delegate;
    CountingRegistry(BeanContext context) { delegate = new DefaultInterceptorRegistry(context); }
    public <T> Interceptor<T, ?>[] resolveInterceptors(Executable<T, ?> method,
            Collection<BeanRegistration<Interceptor<T, ?>>> candidates, InterceptorKind kind) {
        selections++;
        return delegate.resolveInterceptors(method, candidates, kind);
    }
    public <T> Interceptor<T, T>[] resolveConstructorInterceptors(BeanConstructor<T> constructor,
            Collection<BeanRegistration<Interceptor<T, T>>> candidates) {
        return delegate.resolveConstructorInterceptors(constructor, candidates);
    }
}
@Singleton
class Invocations {
    final List<MethodInvocationContext<?, ?>> recorded = new ArrayList<>();
}
'''

    void 'generated proxies build an independent method call each time for #description'() {
        given:
        def context = buildContext(infrastructure + """
@Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.METHOD})
@Around(proxyTarget = $proxyTarget, lazy = $lazy, cacheableLazyTarget = $cached,
        hotswap = $hotswap, lazyInterceptorsPerTarget = $perTarget)
@interface Tracked {}
@Singleton @InterceptorBean(Tracked.class)
class Advice implements MethodInterceptor<Object, Object> {
    private final Invocations invocations;
    Advice(Invocations invocations) { this.invocations = invocations; }
    public Object intercept(MethodInvocationContext<Object, Object> invocation) {
        invocations.recorded.add(invocation);
        return invocation.proceed();
    }
}
@Singleton @Tracked class Subject {
    public String echo(String value) { return value + "!"; }
    public int answer() { return 42; }
}
""")
        def bean = context.getBean(context.classLoader.loadClass('method.factory.Subject'))
        def invocations = context.getBean(context.classLoader.loadClass('method.factory.Invocations')).recorded
        def registry = context.getBean(InterceptorRegistry)

        when:
        def first = bean.echo('first')
        def selections = registry.selections
        def second = bean.echo('second')
        def answer = bean.answer()

        then:
        first == 'first!'
        second == 'second!'
        answer == 42
        invocations.size() == 3
        !invocations[0].is(invocations[1])
        invocations*.methodName == ['echo', 'echo', 'answer']
        selections > 0
        registry.selections == selections

        cleanup:
        context.close()

        where:
        description             | proxyTarget | lazy  | cached | hotswap | perTarget
        'subclass'              | false       | false | false  | false   | false
        'eager target'          | true        | false | false  | false   | false
        'lazy target'           | true        | true  | false  | false   | false
        'cached lazy target'    | true        | true  | true   | false   | false
        'hot swap'              | true        | false | false  | true    | false
        'per-target eager'      | true        | false | false  | false   | true
        'per-target lazy'       | true        | true  | false  | false   | true
        'per-target cached'     | true        | true  | true   | false   | true
        'per-target hot swap'   | true        | false | false  | true    | true
    }

    void 'hot swapping replaces managed and unmanaged targets (perTarget=#perTarget)'() {
        given:
        def context = buildContext(infrastructure + """
@Singleton @Around(proxyTarget = true, hotswap = true, lazyInterceptorsPerTarget = $perTarget)
class Subject {
    public String echo(String value) { return value + "!"; }
    public static Subject unmanaged() { return new Subject(); }
}
""")
        def type = context.classLoader.loadClass('method.factory.Subject')
        def proxy = (HotSwappableInterceptedProxy) context.getBean(type)
        def original = proxy.interceptedTarget()
        def replacement = type.unmanaged()
        def managed = type.unmanaged()
        context.registerSingleton(type, managed, Qualifiers.byName('managed'), false)

        when:
        def swapped = proxy.swap(replacement)

        then:
        swapped.is(original)
        proxy.interceptedTarget().is(replacement)
        proxy.echo('unmanaged') == 'unmanaged!'

        when:
        swapped = proxy.swap(managed)

        then:
        swapped.is(replacement)
        proxy.interceptedTarget().is(managed)
        proxy.echo('managed') == 'managed!'

        cleanup:
        context.close()

        where:
        perTarget << [false, true]
    }

    void 'generated introductions run around advice before introduction advice'() {
        given:
        def context = buildContext(infrastructure + '''
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) @Introduction
@interface Introduce {}
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) @Around
@interface Wrap {}
@Singleton @InterceptorBean(Wrap.class)
class AroundAdvice implements MethodInterceptor<Object, Object> {
    private final Invocations invocations;
    AroundAdvice(Invocations invocations) { this.invocations = invocations; }
    public Object intercept(MethodInvocationContext<Object, Object> invocation) {
        invocations.recorded.add(invocation);
        return "around:" + invocation.proceed();
    }
}
@Singleton @InterceptorBean(Introduce.class)
class IntroductionAdvice implements MethodInterceptor<Object, Object> {
    public Object intercept(MethodInvocationContext<Object, Object> invocation) {
        return "introduced:" + invocation.getParameterValues()[0];
    }
}
@Singleton @Introduce @Wrap interface Subject { String echo(String value); }
''')
        def bean = context.getBean(context.classLoader.loadClass('method.factory.Subject'))
        def invocations = context.getBean(context.classLoader.loadClass('method.factory.Invocations')).recorded

        expect:
        bean.echo('value') == 'around:introduced:value'
        invocations.size() == 1
        invocations[0].kind.name() == 'INTRODUCTION'

        cleanup:
        context.close()
    }
}

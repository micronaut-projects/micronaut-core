package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.InterceptorRegistry
import io.micronaut.aop.chain.InterceptorChainFactory

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
    int methodSelections;
    final InterceptorRegistry delegate;
    CountingRegistry(BeanContext context) { delegate = new DefaultInterceptorRegistry(context); }
    public <T> Interceptor<T, ?>[] resolveMethodInterceptors(ExecutableMethod<T, ?> method,
            Collection<BeanRegistration<Interceptor<T, ?>>> candidates, InterceptorKind kind) {
        methodSelections++;
        return InterceptorRegistry.super.resolveMethodInterceptors(method, candidates, kind);
    }
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
@Singleton @Bean(typed = InterceptorChainFactory.class) @Replaces(InterceptorChainFactory.class)
class CustomFactory implements InterceptorChainFactory {
    final List<MethodInvocationContext<?, ?>> invocations = new ArrayList<>();
    final InterceptorChainFactory delegate;
    CustomFactory(InterceptorRegistry registry) { delegate = new DefaultInterceptorChainFactory(registry); }
    public <T, R> LifecycleInvocation<T, R> buildResolvedInvocation(T bean, ExecutableMethod<T, R> method,
            Interceptor<T, R>[] interceptors, InterceptorKind kind, Object... arguments) {
        LifecycleInvocation<T, R> chain = new RecordingInvocation<>(
            delegate.buildResolvedInvocation(bean, method, interceptors, kind, arguments));
        invocations.add(chain);
        return chain;
    }
    public <T, R> LifecycleInvocation<T, R> buildLifecycleChain(BeanResolutionContext resolution,
            BeanDefinition<T> definition, ExecutableMethod<T, R> method, T bean, InterceptorKind kind,
            Collection<BeanRegistration<Interceptor<?, ?>>> candidates) {
        return delegate.buildLifecycleChain(resolution, definition, method, bean, kind, candidates);
    }
    public <T> ConstructorInvocation<T> buildConstructorChain(BeanResolutionContext resolution,
            BeanDefinition<T> definition, BeanConstructor<T> constructor,
            Collection<BeanRegistration<Interceptor<T, T>>> candidates, int additionalArguments, Object... arguments) {
        return delegate.buildConstructorChain(resolution, definition, constructor, candidates, additionalArguments, arguments);
    }
}
class RecordingInvocation<T, R> implements LifecycleInvocation<T, R> {
    final LifecycleInvocation<T, R> delegate;
    int executions;
    RecordingInvocation(LifecycleInvocation<T, R> delegate) { this.delegate = delegate; }
    public R proceedLifecycle(BeanDefinition<T> definition) { executions++; return delegate.proceedLifecycle(definition); }
    public R proceed() { executions++; return delegate.proceed(); }
    public R proceed(Interceptor from) { return delegate.proceed(from); }
    public R invoke(T bean, Object... arguments) { return delegate.invoke(bean, arguments); }
    public T getTarget() { return delegate.getTarget(); }
    public InterceptorKind getKind() { return delegate.getKind(); }
    public ExecutableMethod<T, R> getExecutableMethod() { return delegate.getExecutableMethod(); }
    public String getMethodName() { return delegate.getMethodName(); }
    public Method getTargetMethod() { return delegate.getTargetMethod(); }
    public ReturnType<R> getReturnType() { return delegate.getReturnType(); }
    public Argument<?>[] getArguments() { return delegate.getArguments(); }
    public Object[] getParameterValues() { return delegate.getParameterValues(); }
    public Map<String, MutableArgumentValue<?>> getParameters() { return delegate.getParameters(); }
    public AnnotationMetadata getAnnotationMetadata() { return delegate.getAnnotationMetadata(); }
    public MutableConvertibleValues<Object> getAttributes() { return delegate.getAttributes(); }
}
'''

    void 'replacement factory builds independent method calls for #description'() {
        given:
        def context = buildContext(infrastructure + """
@Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.METHOD})
@Around(proxyTarget = $proxyTarget, lazy = $lazy, cacheableLazyTarget = $cached,
        hotswap = $hotswap, lazyInterceptorsPerTarget = $perTarget)
@interface Tracked {}
@Singleton @InterceptorBean(Tracked.class)
class Advice implements MethodInterceptor<Object, Object> {
    public Object intercept(MethodInvocationContext<Object, Object> invocation) { return invocation.proceed(); }
}
@Singleton @Tracked class Subject {
    public String echo(String value) { return value + "!"; }
    public int answer() { return 42; }
}
""")
        def bean = context.getBean(context.classLoader.loadClass('method.factory.Subject'))
        def factory = context.getBean(InterceptorChainFactory)
        def registry = context.getBean(InterceptorRegistry)

        when:
        def first = bean.echo('first')
        def selections = registry.selections
        def methodSelections = registry.methodSelections
        def second = bean.echo('second')
        def answer = bean.answer()

        then:
        first == 'first!'
        second == 'second!'
        answer == 42
        factory.invocations.size() == 3
        !factory.invocations[0].is(factory.invocations[1])
        factory.invocations*.methodName == ['echo', 'echo', 'answer']
        registry.selections == selections
        methodSelections > 0
        registry.methodSelections == methodSelections
        factory.invocations*.executions == [1, 1, 1]

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

    void 'replacement factory preserves introduction and around ordering'() {
        given:
        def context = buildContext(infrastructure + '''
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) @Introduction
@interface Introduce {}
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) @Around
@interface Wrap {}
@Singleton @InterceptorBean(Wrap.class)
class AroundAdvice implements MethodInterceptor<Object, Object> {
    public Object intercept(MethodInvocationContext<Object, Object> invocation) { return "around:" + invocation.proceed(); }
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
        def factory = context.getBean(InterceptorChainFactory)

        expect:
        bean.echo('value') == 'around:introduced:value'
        factory.invocations.size() == 1
        factory.invocations[0].kind.name() == 'INTRODUCTION'
        factory.invocations[0].executions == 1

        cleanup:
        context.close()
    }
}

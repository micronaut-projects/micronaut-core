package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.InterceptorRegistry

class ConstructorChainFactorySpec extends AbstractTypeElementSpec {
    void 'default factory subclasses can decorate independent constructor invocations'() {
        given:
        def context = buildContext('''
package constructor.factory;
import io.micronaut.aop.*;
import io.micronaut.aop.chain.*;
import io.micronaut.context.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.inject.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Singleton @Bean(typed = InterceptorRegistry.class) @Replaces(InterceptorRegistry.class)
class CustomRegistry implements InterceptorRegistry {
    final InterceptorRegistry delegate;
    final CustomFactory factory = new CustomFactory(this);
    CustomRegistry(BeanContext context) { delegate = new DefaultInterceptorRegistry(context); }
    public InterceptorChainFactory chainFactory() { return factory; }
    public <T> Interceptor<T, ?>[] resolveInterceptors(Executable<T, ?> method,
            Collection<BeanRegistration<Interceptor<T, ?>>> candidates, InterceptorKind kind) {
        return delegate.resolveInterceptors(method, candidates, kind);
    }
    public <T> Interceptor<T, T>[] resolveConstructorInterceptors(BeanConstructor<T> constructor,
            Collection<BeanRegistration<Interceptor<T, T>>> candidates) {
        return delegate.resolveConstructorInterceptors(constructor, candidates);
    }
}
class CustomFactory extends DefaultInterceptorChainFactory {
    final List<RecordingInvocation<?>> invocations = new ArrayList<>();
    CustomFactory(InterceptorRegistry registry) { super(registry); }
    public <T> ConstructorInvocation<T> buildConstructorChain(BeanResolutionContext resolution,
            BeanDefinition<T> definition, BeanConstructor<T> constructor,
            Collection<BeanRegistration<Interceptor<T, T>>> candidates, int additionalArguments, Object... arguments) {
        RecordingInvocation<T> invocation = new RecordingInvocation<>(
            super.buildConstructorChain(resolution, definition, constructor, candidates, additionalArguments, arguments));
        invocations.add(invocation);
        return invocation;
    }
}
class RecordingInvocation<T> implements ConstructorInvocation<T> {
    final ConstructorInvocation<T> delegate;
    int executions;
    RecordingInvocation(ConstructorInvocation<T> delegate) { this.delegate = delegate; }
    public T instantiate() { executions++; return delegate.instantiate(); }
    public T proceed() { return delegate.proceed(); }
    public T proceed(Interceptor from) { return delegate.proceed(from); }
    public T invoke(T bean, Object... arguments) { return delegate.invoke(bean, arguments); }
    public T getTarget() { return delegate.getTarget(); }
    public InterceptorKind getKind() { return delegate.getKind(); }
    public BeanConstructor<T> getConstructor() { return delegate.getConstructor(); }
    public Argument<?>[] getArguments() { return delegate.getArguments(); }
    public Object[] getParameterValues() { return delegate.getParameterValues(); }
    public Map<String, MutableArgumentValue<?>> getParameters() { return delegate.getParameters(); }
    public AnnotationMetadata getAnnotationMetadata() { return delegate.getAnnotationMetadata(); }
    public MutableConvertibleValues<Object> getAttributes() { return delegate.getAttributes(); }
}
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) @AroundConstruct
@interface Tracked {}
@Singleton @InterceptorBean(Tracked.class)
class Advice implements ConstructorInterceptor<Object> {
    public Object intercept(ConstructorInvocationContext<Object> invocation) {
        invocation.getParameterValues()[0] += "!";
        return invocation.proceed();
    }
}
@Prototype @Tracked class Subject {
    final String value;
    Subject(@Parameter String value) { this.value = value; }
}
''')
        def type = context.classLoader.loadClass('constructor.factory.Subject')

        when:
        def first = context.createBean(type, [value: 'first'])
        def second = context.createBean(type, [value: 'second'])
        def invocations = context.getBean(InterceptorRegistry).chainFactory().invocations

        then:
        first.value == 'first!'
        second.value == 'second!'
        invocations.size() == 2
        !invocations[0].is(invocations[1])
        invocations*.executions == [1, 1]
        invocations*.parameterValues == [['first!'], ['second!']]
        invocations*.kind*.name() == ['AROUND_CONSTRUCT', 'AROUND_CONSTRUCT']

        cleanup:
        context.close()
    }
}

package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.InterceptorRegistry
import io.micronaut.aop.chain.InterceptorChainFactory

class LifecycleInterceptorRegistrySpec extends AbstractTypeElementSpec {
    void 'a replacement chain factory handles framework construction initialization and destruction'() {
        given:
        def context = buildContext('''
package lifecycle.factory;
import io.micronaut.aop.*;
import io.micronaut.aop.chain.*;
import io.micronaut.context.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.inject.*;
import jakarta.annotation.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Singleton @Bean(typed = InterceptorChainFactory.class) @Replaces(InterceptorChainFactory.class)
class CustomFactory implements InterceptorChainFactory {
    final List<InterceptorKind> seen = new ArrayList<>();
    final InterceptorChainFactory delegate;
    CustomFactory(InterceptorRegistry registry) { delegate = new DefaultInterceptorChainFactory(registry); }
    public <T, R> LifecycleInvocation<T, R> buildLifecycleChain(BeanResolutionContext resolution,
            BeanDefinition<T> definition, ExecutableMethod<T, R> method, T bean, InterceptorKind kind,
            Collection<BeanRegistration<Interceptor<?, ?>>> candidates) {
        seen.add(kind);
        return delegate.buildLifecycleChain(resolution, definition, method, bean, kind, candidates);
    }
    public <T, R> MethodInvocationContext<T, R> buildMethodChain(T bean, ExecutableMethod<T, R> method,
            Collection<BeanRegistration<Interceptor<T, ?>>> candidates, InterceptorKind kind, Object... arguments) {
        return delegate.buildMethodChain(bean, method, candidates, kind, arguments);
    }
    public <T> ConstructorInvocation<T> buildConstructorChain(BeanResolutionContext resolution,
            BeanDefinition<T> definition, BeanConstructor<T> constructor,
            Collection<BeanRegistration<Interceptor<T, T>>> candidates, int additionalArguments, Object... arguments) {
        seen.add(InterceptorKind.AROUND_CONSTRUCT);
        return delegate.buildConstructorChain(resolution, definition, constructor, candidates, additionalArguments, arguments);
    }
}
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {}
@Singleton @InterceptorBean(Tracked.class)
class Advice implements Interceptor<Object, Object> {
    public Object intercept(InvocationContext<Object, Object> context) { return context.proceed(); }
}
@Prototype @Tracked class Subject {
    int lifecycleCalls;
    @PostConstruct void initialize() { lifecycleCalls++; }
    @PreDestroy void dispose() { lifecycleCalls++; }
}
''')

        when:
        def registration = context.getBeanRegistration(context.classLoader.loadClass('lifecycle.factory.Subject'), null)
        def factory = context.getBean(InterceptorChainFactory)

        then:
        factory.class.simpleName == 'CustomFactory'
        factory.seen == [io.micronaut.aop.InterceptorKind.AROUND_CONSTRUCT, io.micronaut.aop.InterceptorKind.POST_CONSTRUCT]
        registration.bean.lifecycleCalls == 1

        when:
        context.destroyBean(registration)

        then:
        factory.seen*.name() == ['AROUND_CONSTRUCT', 'POST_CONSTRUCT', 'PRE_DESTROY']
        registration.bean.lifecycleCalls == 2

        cleanup:
        context.close()
    }

    void 'lifecycle service preserves custom registry selection and context isolation'() {
        given:
        def source = '''
package lifecycle.registry;
import io.micronaut.aop.*;
import io.micronaut.aop.chain.DefaultInterceptorRegistry;
import io.micronaut.context.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Executable;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Singleton @Bean(typed = InterceptorRegistry.class) @Replaces(InterceptorRegistry.class)
class CustomRegistry implements InterceptorRegistry {
    final List<InterceptorKind> seen = new ArrayList<>();
    final InterceptorRegistry delegate;
    CustomRegistry(BeanContext context) { delegate = new DefaultInterceptorRegistry(context); }
    public <T> Interceptor<T, ?>[] resolveInterceptors(Executable<T, ?> method,
            Collection<BeanRegistration<Interceptor<T, ?>>> candidates, InterceptorKind kind) {
        seen.add(kind);
        return delegate.resolveInterceptors(method, candidates, kind);
    }
    public <T> Interceptor<T, T>[] resolveConstructorInterceptors(BeanConstructor<T> constructor,
            Collection<BeanRegistration<Interceptor<T, T>>> candidates) {
        seen.add(InterceptorKind.AROUND_CONSTRUCT);
        return delegate.resolveConstructorInterceptors(constructor, candidates);
    }
}
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {}
@Singleton @InterceptorBean(Tracked.class)
class Advice implements Interceptor<Object, Object> {
    public Object intercept(InvocationContext<Object, Object> context) { return context.proceed(); }
}
@Prototype @Tracked class Subject {}
'''
        def first = buildContext(source)
        def second = buildContext(source)

        when:
        def firstBean = first.getBeanRegistration(first.classLoader.loadClass('lifecycle.registry.Subject'), null)
        def secondBean = second.getBeanRegistration(second.classLoader.loadClass('lifecycle.registry.Subject'), null)
        def firstRegistry = first.getBean(InterceptorRegistry)
        def secondRegistry = second.getBean(InterceptorRegistry)

        then:
        first.getBean(InterceptorChainFactory).is(first.getBean(InterceptorChainFactory))
        !first.getBean(InterceptorChainFactory).is(second.getBean(InterceptorChainFactory))
        !firstRegistry.is(secondRegistry)
        firstRegistry.seen*.name() == ['AROUND_CONSTRUCT', 'POST_CONSTRUCT']
        secondRegistry.seen*.name() == ['AROUND_CONSTRUCT', 'POST_CONSTRUCT']

        when:
        first.destroyBean(firstBean)

        then:
        firstRegistry.seen*.name() == ['AROUND_CONSTRUCT', 'POST_CONSTRUCT', 'PRE_DESTROY']
        secondRegistry.seen*.name() == ['AROUND_CONSTRUCT', 'POST_CONSTRUCT']

        when:
        second.destroyBean(secondBean)

        then:
        secondRegistry.seen*.name() == ['AROUND_CONSTRUCT', 'POST_CONSTRUCT', 'PRE_DESTROY']

        cleanup:
        first.close()
        second.close()
    }
}

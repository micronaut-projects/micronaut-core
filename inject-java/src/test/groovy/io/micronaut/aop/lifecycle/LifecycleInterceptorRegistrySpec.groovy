package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.InterceptorRegistry

class LifecycleInterceptorRegistrySpec extends AbstractTypeElementSpec {
    void 'construction, initialization and destruction run their advice through the chain factory'() {
        given:
        def context = buildContext('''
package lifecycle.factory;
import io.micronaut.aop.*;
import io.micronaut.aop.chain.*;
import io.micronaut.context.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Executable;
import io.micronaut.inject.*;
import jakarta.annotation.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {}
@Singleton @InterceptorBean(Tracked.class)
class Advice implements Interceptor<Object, Object> {
    final List<InterceptorKind> seen = new ArrayList<>();
    public Object intercept(InvocationContext<Object, Object> context) {
        seen.add(context.getKind());
        return context.proceed();
    }
}
@Prototype @Tracked class Subject {
    int lifecycleCalls;
    @PostConstruct void initialize() { lifecycleCalls++; }
    @PreDestroy void dispose() { lifecycleCalls++; }
}
''')

        when:
        def registration = context.getBeanRegistration(context.classLoader.loadClass('lifecycle.factory.Subject'), null)
        def advice = context.getBean(context.classLoader.loadClass('lifecycle.factory.Advice'))

        then:
        advice.seen*.name() == ['AROUND_CONSTRUCT', 'POST_CONSTRUCT']
        registration.bean.lifecycleCalls == 1

        when:
        context.destroyBean(registration)

        then:
        advice.seen*.name() == ['AROUND_CONSTRUCT', 'POST_CONSTRUCT', 'PRE_DESTROY']
        registration.bean.lifecycleCalls == 2

        cleanup:
        context.close()
    }

    void 'lifecycle selection goes through a replacement registry, separately in each context'() {
        given:
        def source = '''
package lifecycle.registry;
import io.micronaut.aop.*;
import io.micronaut.aop.chain.DefaultInterceptorRegistry;
import io.micronaut.context.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.inject.*;
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

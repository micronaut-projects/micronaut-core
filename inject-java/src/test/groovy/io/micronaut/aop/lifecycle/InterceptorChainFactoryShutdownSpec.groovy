package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.chain.InterceptorChainFactory

class InterceptorChainFactoryShutdownSpec extends AbstractTypeElementSpec {
    void 'replacement factory outlives #mode lifecycle consumers at shutdown'() {
        given:
        def source = '''
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
class AAAFactory extends DefaultInterceptorChainFactory {
    final List<InterceptorKind> seen = new ArrayList<>();
    AAAFactory(InterceptorRegistry registry) {
        super(registry);
        Events.values.add("created");
    }
    @PreDestroy void close() { Events.values.add("closed"); }
    public <T, R> LifecycleInvocation<T, R> buildResolvedInvocation(T bean, ExecutableMethod<T, R> method,
            Interceptor<T, R>[] interceptors, InterceptorKind kind, Object... arguments) {
        seen.add(kind);
        if (kind == InterceptorKind.PRE_DESTROY) {
            Events.values.add("dispose");
        }
        return super.buildResolvedInvocation(bean, method, interceptors, kind, arguments);
    }
    public <T> ConstructorInvocation<T> buildConstructorChain(BeanResolutionContext resolution,
            BeanDefinition<T> definition, BeanConstructor<T> constructor,
            Collection<BeanRegistration<Interceptor<T, T>>> candidates, int additionalArguments, Object... arguments) {
        seen.add(InterceptorKind.AROUND_CONSTRUCT);
        return super.buildConstructorChain(resolution, definition, constructor, candidates, additionalArguments, arguments);
    }
}
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
CONSTRUCTION_BINDING
INITIALIZATION_BINDING
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {}
@Singleton @InterceptorBean(Tracked.class)
class Advice implements Interceptor<Object, Object> {
    public Object intercept(InvocationContext<Object, Object> context) { return context.proceed(); }
}
class Events { static final List<String> values = new ArrayList<>(); }
@Singleton @Tracked class Subject {
    int lifecycleCalls;
    @PostConstruct void initialize() { lifecycleCalls++; }
    @PreDestroy void dispose() { lifecycleCalls++; Events.values.add("subject"); }
}
'''
        def context = buildContext(source
            .replace('CONSTRUCTION_BINDING', mode == 'all phases' ? '@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)' : '')
            .replace('INITIALIZATION_BINDING', mode == 'all phases' ? '@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)' : ''))

        when:
        def registration = context.getBeanRegistration(context.classLoader.loadClass('lifecycle.factory.Subject'), null)
        def factory = context.getBean(InterceptorChainFactory)

        then:
        factory.class.simpleName == 'AAAFactory'
        factory.seen*.name() == (mode == 'all phases' ? ['AROUND_CONSTRUCT', 'POST_CONSTRUCT'] : [])
        registration.bean.lifecycleCalls == 1

        when:
        context.close()

        then:
        context.classLoader.loadClass('lifecycle.factory.Events').values == ['created', 'dispose', 'subject', 'closed']
        registration.bean.lifecycleCalls == 2

        cleanup:
        context.close()

        where:
        mode << ['all phases', 'pre-destroy only']
    }

}

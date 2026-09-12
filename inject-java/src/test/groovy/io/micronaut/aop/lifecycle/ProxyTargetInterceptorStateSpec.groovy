package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.aop.InterceptorRegistry
import spock.util.concurrent.PollingConditions

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * How a proxy with {@code proxyTarget = true} shares non-singleton interceptors with its targets: none are created for
 * the proxy itself, targets resolved concurrently keep their own, a replaced registry still sees its instances, runtime
 * proxies behave like generated ones, and nothing retains a target that a scope forgot.
 */
class ProxyTargetInterceptorStateSpec extends AbstractTypeElementSpec {

    private static final String SCOPE = '''
@ScopedProxy
@Scope
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface Conversation {
}

@Singleton
class ConversationScope extends AbstractConcurrentCustomScope<Conversation> {
    static String current = "first";
    private final Map<String, Map<BeanIdentifier, CreatedBean<?>>> conversations = new ConcurrentHashMap<>();

    ConversationScope() {
        super(Conversation.class);
    }

    @Override
    protected Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean forCreation) {
        return conversations.computeIfAbsent(current, k -> new ConcurrentHashMap<>());
    }

    void end(String conversation) {
        destroyScope(conversations.remove(conversation));
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public void close() {
        conversations.values().forEach(this::destroyScope);
        conversations.clear();
    }
}
'''

    private static final String IMPORTS = '''
import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.scope.AbstractConcurrentCustomScope;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.runtime.context.scope.ScopedProxy;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
'''


    void 'construction and method-only bindings share target ownership without creating unused proxy interceptors'() {
        given:
        ApplicationContext context = buildContext("""
package scopedproxy.state;
$IMPORTS
$SCOPE

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Around
@interface MethodOnly {}

@Prototype
@InterceptorBean(Tracked.class)
class Tracking implements Interceptor<Object, Object> {
    static int instances;
    static final List<String> events = new ArrayList<>();
    final int id = ++instances;
    public Object intercept(InvocationContext<Object, Object> ctx) {
        events.add(id + ":" + ctx.getKind());
        return ctx.proceed();
    }
    @PreDestroy void close() { events.add(id + ":DESTROYED"); }
}

@Prototype
@InterceptorBean(MethodOnly.class)
class MethodTracking implements MethodInterceptor<Object, Object> {
    static int instances;
    static final List<String> events = new ArrayList<>();
    final int id = ++instances;
    int calls;
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        events.add(id + ":" + ++calls);
        return ctx.proceed();
    }
    @PreDestroy void close() { events.add(id + ":DESTROYED"); }
}

@Conversation
@Tracked
class TargetBean {
    @PostConstruct void init() {}
    @MethodOnly public String call(String value) { return value; }
    @PreDestroy void close() { Tracking.events.add("target:DESTROYED"); }
    // An identity lookup must not invoke user equality, including when different targets compare equal.
    public boolean equals(Object other) { return other instanceof TargetBean; }
    public int hashCode() { throw new AssertionError("User hashCode called"); }
}
""")
        def tracking = context.classLoader.loadClass('scopedproxy.state.Tracking')
        def methodTracking = context.classLoader.loadClass('scopedproxy.state.MethodTracking')
        def scopeType = context.classLoader.loadClass('scopedproxy.state.ConversationScope')
        def scope = context.getBean(scopeType)
        def bean = context.getBean(context.classLoader.loadClass('scopedproxy.state.TargetBean'))

        expect:
        tracking.instances == 0
        methodTracking.instances == 0

        when:
        bean.call('one')
        bean.call('two')
        scopeType.current = 'second'
        bean.call('three')
        scope.end('first')
        scope.end('second')

        then:
        tracking.instances == 2
        methodTracking.instances == 2
        tracking.events == [
            '1:AROUND_CONSTRUCT', '1:POST_CONSTRUCT', '1:AROUND', '1:AROUND',
            '2:AROUND_CONSTRUCT', '2:POST_CONSTRUCT', '2:AROUND',
            '1:PRE_DESTROY', 'target:DESTROYED', '1:DESTROYED',
            '2:PRE_DESTROY', 'target:DESTROYED', '2:DESTROYED'
        ]
        methodTracking.events == ['1:1', '1:2', '2:1', '1:DESTROYED', '2:DESTROYED']

        cleanup:
        scopeType.current = 'first'
        context.close()
    }

    void 'concurrent targets do not share creation state through a lazy proxy resolution context'() {
        given:
        def context = buildContext('scopedproxy.concurrent.TargetBean', '''
package scopedproxy.concurrent;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.runtime.context.scope.ThreadLocal;
import jakarta.annotation.PostConstruct;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Around
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {}

@Prototype
@InterceptorBean(Tracked.class)
class Tracking implements Interceptor<Object, Object> {
    static final AtomicInteger instances = new AtomicInteger();
    static final CyclicBarrier constructing = new CyclicBarrier(2);
    static final Map<String, Set<Integer>> seen = new ConcurrentHashMap<>();
    final int id = instances.incrementAndGet();
    public Object intercept(InvocationContext<Object, Object> ctx) {
        seen.computeIfAbsent(Thread.currentThread().getName(), k -> ConcurrentHashMap.newKeySet()).add(id);
        if (ctx.getKind() == InterceptorKind.AROUND_CONSTRUCT) {
            try { constructing.await(10, TimeUnit.SECONDS); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        return ctx.proceed();
    }
}

@ThreadLocal
@Tracked
class TargetBean {
    @PostConstruct void init() {}
    public String call() { return "called"; }
}
''', true)
        def bean = context.getBean(context.classLoader.loadClass('scopedproxy.concurrent.TargetBean'))
        def tracking = context.classLoader.loadClass('scopedproxy.concurrent.Tracking')
        def failures = new ConcurrentLinkedQueue<Throwable>()

        when:
        def threads = (1..2).collect { n ->
            Thread.start("target-$n") {
                try {
                    bean.call()
                    bean.call()
                } catch (Throwable e) {
                    failures.add(e)
                }
            }
        }
        threads*.join()

        then:
        failures.empty
        tracking.instances.get() == 2
        tracking.seen.size() == 2
        tracking.seen.values().every { it.size() == 1 }
        tracking.seen['target-1'] != tracking.seen['target-2']

        cleanup:
        context.close()
    }

    void 'a custom registry exposed only as InterceptorRegistry keeps its existing resolution path'() {
        given:
        def context = buildContext('''
package scopedproxy.customregistry;
import io.micronaut.aop.*;
import io.micronaut.aop.chain.DefaultInterceptorRegistry;
import io.micronaut.context.*;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Executable;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.Collection;

@Singleton
@Bean(typed = InterceptorRegistry.class)
@Replaces(InterceptorRegistry.class)
class CustomRegistry implements InterceptorRegistry {
    final DefaultInterceptorRegistry delegate;
    CustomRegistry(BeanContext context) { delegate = new DefaultInterceptorRegistry(context); }
    public <T> Interceptor<T, ?>[] resolveInterceptors(Executable<T, ?> method,
            Collection<BeanRegistration<Interceptor<T, ?>>> interceptors, InterceptorKind kind) {
        if (interceptors.stream().noneMatch(registration -> registration.getBean() instanceof Tracking)) {
            throw new AssertionError("Custom registry must receive its usual interceptor instances");
        }
        return delegate.resolveInterceptors(method, interceptors, kind);
    }
    public <T> Interceptor<T, T>[] resolveConstructorInterceptors(BeanConstructor<T> constructor,
            Collection<BeanRegistration<Interceptor<T, T>>> interceptors) {
        return delegate.resolveConstructorInterceptors(constructor, interceptors);
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Around(proxyTarget = true)
@interface Tracked {}

@Prototype
@InterceptorBean(Tracked.class)
class Tracking implements MethodInterceptor<Object, Object> {
    int calls;
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        return ctx.proceed() + ":" + ++calls;
    }
}

@Singleton
@Tracked
class TargetBean {
    public String call() { return "called"; }
}
''')
        def bean = context.getBean(context.classLoader.loadClass('scopedproxy.customregistry.TargetBean'))

        expect:
        context.getBean(InterceptorRegistry).class.name == 'scopedproxy.customregistry.CustomRegistry'
        bean.call() == 'called:1'
        bean.call() == 'called:2'

        cleanup:
        context.close()
    }

    void 'a runtime proxy uses its targets lifecycle interceptor for method calls'() {
        given:
        def context = buildContext('''
package scopedproxy.runtime;
import io.micronaut.aop.*;
import io.micronaut.aop.runtime.RuntimeProxy;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Singleton;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Around(proxyTarget = true)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {}

@Prototype
@InterceptorBean(Tracked.class)
class Tracking implements MethodInterceptor<Object, Object> {
    static int instances;
    static final List<String> events = new ArrayList<>();
    final int id = ++instances;
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        events.add(id + ":" + ctx.getKind());
        return ctx.proceed();
    }
    @PreDestroy void close() { events.add(id + ":DESTROYED"); }
}

@Singleton
@Tracked
@RuntimeProxy(io.micronaut.aop.ByteBuddyRuntimeProxy.class)
class TargetBean {
    @PostConstruct void init() {}
    public String call() { return "called"; }
    @PreDestroy void close() { Tracking.events.add("target:DESTROYED"); }
}
''')
        context.registerSingleton(new io.micronaut.aop.ByteBuddyRuntimeProxy())
        def tracking = context.classLoader.loadClass('scopedproxy.runtime.Tracking')

        when:
        context.getBean(context.classLoader.loadClass('scopedproxy.runtime.TargetBean')).call()
        context.stop()

        then:
        tracking.instances == 1
        tracking.events == ['1:POST_CONSTRUCT', '1:AROUND', '1:PRE_DESTROY', 'target:DESTROYED', '1:DESTROYED']

        cleanup:
        context.close()
    }

    void 'thread local targets and interceptors retaining their targets can be collected without destruction'() {
        given:
        ApplicationContext context = buildContext('scopedproxy.collection.TargetBean', '''
package scopedproxy.collection;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.runtime.context.scope.ThreadLocal;
import java.lang.annotation.*;
import java.lang.ref.WeakReference;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Around
@interface Tracked {}

@Prototype
@InterceptorBean(Tracked.class)
class Tracking implements MethodInterceptor<Object, Object> {
    static final List<WeakReference<Object>> references = Collections.synchronizedList(new ArrayList<>());
    Object target;
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        target = ctx.getTarget();
        references.add(new WeakReference<>(target));
        references.add(new WeakReference<>(this));
        return ctx.proceed();
    }
}

@ThreadLocal
@Tracked
class TargetBean {
    public String call() { return "called"; }
}
''', true)
        def bean = context.getBean(context.classLoader.loadClass('scopedproxy.collection.TargetBean'))
        def tracking = context.classLoader.loadClass('scopedproxy.collection.Tracking')

        when:
        Thread.start { bean.call() }.join()

        then:
        tracking.references.size() == 2
        new PollingConditions(timeout: 10).eventually {
            System.gc()
            assert tracking.references.every { it.get() == null }
        }

        cleanup:
        context.close()
    }
}

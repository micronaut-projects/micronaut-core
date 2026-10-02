package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.type.Argument
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import io.micronaut.runtime.context.scope.refresh.RefreshScope

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class InterceptorDependencyOwnershipSpec extends AbstractTypeElementSpec {
    private static final String IMPORTS = '''
package test;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;
import jakarta.annotation.*;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.*;

class Events {
    static final List<String> LOG = new CopyOnWriteArrayList<>();
}
'''

    void 'a shared interceptor outlives its #scope target (lazy=#lazy)'() {
        given:
        def ctx = buildContext(IMPORTS + """
@Retention(RetentionPolicy.RUNTIME)
@InterceptorBinding(kind = InterceptorKind.AROUND)
@interface Traced {}
@Singleton @InterceptorBean(Traced.class)
class AInterceptor implements MethodInterceptor<Object, Object> {
    static int created;
    static boolean closed;
    AInterceptor() { created++; }
    public Object intercept(MethodInvocationContext<Object, Object> invocation) {
        if (closed) throw new IllegalStateException("interceptor already closed");
        return invocation.proceed();
    }
    @PreDestroy void close() { closed = true; Events.LOG.add("interceptor"); }
}
@$scope @Around(proxyTarget = true, lazy = $lazy, cacheableLazyTarget = true, lazyInterceptorsPerTarget = true)
class ZTarget {
    @Traced public void run() { }
    @PreDestroy void close() { Events.LOG.add("target:" + AInterceptor.closed); }
}
@Singleton class ZOwner {
    final ZTarget target;
    ZOwner(ZTarget target) { this.target = target; }
    @PreDestroy void close() {
        target.run();
        Events.LOG.add("owner");
    }
}
""")
        def owner = ctx.getBean(ctx.classLoader.loadClass('test.ZOwner'))
        def interceptor = ctx.classLoader.loadClass('test.AInterceptor')
        def events = ctx.classLoader.loadClass('test.Events')

        expect: 'a lazy proxy does not initialize its target or interceptors'
        interceptor.created == (lazy ? 0 : 1)

        when:
        owner.target.run()
        owner.target.run()
        ctx.close()

        then:
        interceptor.created == 1
        events.LOG == ['owner', 'target:false', 'interceptor']

        where:
        scope       | lazy
        'Singleton' | false
        'Singleton' | true
        'Prototype' | false
        'Prototype' | true
    }

    void 'refresh replaces target-owned interceptors while retaining shared ones'() {
        given:
        def ctx = buildContext('test.ZTarget', IMPORTS + '''
@Retention(RetentionPolicy.RUNTIME)
@Around(lazyInterceptorsPerTarget = true)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Traced {}
@Singleton @InterceptorBean(Traced.class)
class AShared implements MethodInterceptor<Object, Object> {
    static int created;
    static boolean closed;
    AShared() { created++; }
    public Object intercept(MethodInvocationContext<Object, Object> invocation) {
        if (closed) throw new IllegalStateException("shared interceptor already closed");
        return invocation.proceed();
    }
    @PreDestroy void close() { closed = true; Events.LOG.add("shared"); }
}
@Prototype @InterceptorBean(Traced.class)
class BLocal implements MethodInterceptor<Object, Object> {
    static int created;
    final int id = ++created;
    public Object intercept(MethodInvocationContext<Object, Object> invocation) {
        Events.LOG.add(id + ":" + invocation.getKind());
        return invocation.proceed();
    }
    @PreDestroy void close() { Events.LOG.add(id + ":closed"); }
}
@io.micronaut.runtime.context.scope.Refreshable @Traced class ZTarget {
    public void run() { }
    @PostConstruct void init() { }
    @PreDestroy void close() { Events.LOG.add("target"); }
}
''', true)
        def target = ctx.getBean(ctx.classLoader.loadClass('test.ZTarget'))
        def shared = ctx.classLoader.loadClass('test.AShared')
        def local = ctx.classLoader.loadClass('test.BLocal')
        def events = ctx.classLoader.loadClass('test.Events')

        when:
        target.run()
        ctx.getBean(RefreshScope).onRefreshEvent(new RefreshEvent())
        target.run()

        then:
        shared.created == 1
        !shared.closed
        local.created == 2
        events.LOG == ['1:POST_CONSTRUCT', '1:AROUND', '1:PRE_DESTROY', 'target', '1:closed',
                       '2:POST_CONSTRUCT', '2:AROUND']

        when: 'ending the scope destroys its target and owned interceptors, but not the shared interceptor'
        ctx.getBean(RefreshScope).onRefreshEvent(new RefreshEvent())

        then:
        !shared.closed
        events.LOG[-3..-1] == ['2:PRE_DESTROY', 'target', '2:closed']

        cleanup:
        ctx.close()
    }

    void 'native interceptor selection racing target destruction rolls back new interceptors'() {
        given:
        def ctx = buildContext(IMPORTS + '''
@Prototype
class SlowInterceptor implements MethodInterceptor<Object, Object> {
    static final CountDownLatch ENTERED = new CountDownLatch(1);
    static final CountDownLatch RELEASE = new CountDownLatch(1);
    SlowInterceptor() throws InterruptedException {
        ENTERED.countDown();
        if (!RELEASE.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
    }
    public Object intercept(MethodInvocationContext<Object, Object> invocation) { return invocation.proceed(); }
    @PreDestroy void close() { Events.LOG.add("interceptor"); }
}
@Singleton class ZTarget {
    static final CountDownLatch DESTROYING = new CountDownLatch(1);
    @PreDestroy void close() { Events.LOG.add("target"); DESTROYING.countDown(); }
}
''')
        def type = ctx.classLoader.loadClass('test.ZTarget')
        def target = ctx.getBeanRegistration(type, null)
        def interceptor = ctx.classLoader.loadClass('test.SlowInterceptor')
        def events = ctx.classLoader.loadClass('test.Events')
        def call = CompletableFuture.supplyAsync {
            target.selectInterceptors(new Object()) { resolution ->
                resolution.getInterceptorRegistrations(Argument.of(interceptor), null)
            }
        }
        assert interceptor.ENTERED.await(10, TimeUnit.SECONDS)
        def destruction = CompletableFuture.runAsync { target.close() }
        assert type.DESTROYING.await(10, TimeUnit.SECONDS)

        when:
        interceptor.RELEASE.countDown()
        call.get(10, TimeUnit.SECONDS)

        then:
        def failure = thrown(ExecutionException)
        failure.cause instanceof IllegalStateException

        when:
        destruction.get(10, TimeUnit.SECONDS)

        then:
        events.LOG == ['target', 'interceptor']

        cleanup:
        interceptor.RELEASE.countDown()
        ctx.close()
    }
    void 'an existing registration records shared dependencies selected later'() {
        given:
        def ctx = buildContext(IMPORTS + '''
@Singleton class AInterceptor implements MethodInterceptor<Object, Object> {
    static int created;
    static boolean closed;
    AInterceptor() { created++; }
    public Object intercept(MethodInvocationContext<Object, Object> invocation) { return invocation.proceed(); }
    @PreDestroy void close() { closed = true; Events.LOG.add("interceptor"); }
}
@Singleton class ZTarget {
    @PreDestroy void close() { Events.LOG.add("target:" + AInterceptor.closed); }
}
''')
        def type = ctx.classLoader.loadClass('test.ZTarget')
        def interceptor = ctx.classLoader.loadClass('test.AInterceptor')
        def target = ctx.getBeanRegistration(type, null)
        def events = ctx.classLoader.loadClass('test.Events')
        def key = new Object()
        int selections = 0

        expect:
        interceptor.created == 0

        when:
        def selected = target.selectInterceptors(key) { resolution ->
            selections++
            resolution.getInterceptorRegistrations(Argument.of(interceptor), null).first().bean
        }
        def reused = target.selectInterceptors(key) { throw new AssertionError('selection was not cached') }
        assert target.getInterceptorSelection(key).is(selected)
        ctx.close()

        then:
        selected.is(reused)
        selections == 1
        events.LOG == ['target:false', 'interceptor']
    }

    void 'an unmanaged selection racing context shutdown releases its interceptors'() {
        given:
        def ctx = buildContext(IMPORTS + '''
@Prototype class UnmanagedInterceptor implements MethodInterceptor<Object, Object> {
    public Object intercept(MethodInvocationContext<Object, Object> invocation) { return invocation.proceed(); }
    @PreDestroy void close() { Events.LOG.add("interceptor"); }
}
''')
        def interceptor = ctx.classLoader.loadClass('test.UnmanagedInterceptor')
        def events = ctx.classLoader.loadClass('test.Events')
        def entered = new java.util.concurrent.CountDownLatch(1)
        def release = new java.util.concurrent.CountDownLatch(1)
        def selection = CompletableFuture.supplyAsync {
            ctx.selectUnownedInterceptors(new Object(), Argument.of(interceptor), null) { registrations ->
                entered.countDown()
                assert release.await(10, TimeUnit.SECONDS)
                registrations
            }
        }
        assert entered.await(10, TimeUnit.SECONDS)

        when:
        ctx.close()
        release.countDown()
        selection.get(10, TimeUnit.SECONDS)

        then:
        def failure = thrown(ExecutionException)
        failure.cause instanceof IllegalStateException
        failure.cause.suppressed.length == 0
        events.LOG == ['interceptor']

        cleanup:
        release.countDown()
        ctx.close()
    }

}

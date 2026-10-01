package io.micronaut.inject.dependencies

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.BeanDependencyResolver
import io.micronaut.context.exceptions.BeanInstantiationException

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class BeanDependencyResolverSpec extends AbstractTypeElementSpec {
    private static final String HEADER = '''
package test;
import io.micronaut.context.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.*;
import jakarta.annotation.*;
import java.util.*;
import java.util.concurrent.*;

class Log {
    static final List<String> EVENTS = new CopyOnWriteArrayList<>();
    static int next;
}
@Prototype class Resource {
    final int id = ++Log.next;
    @PreDestroy void close() { Log.EVENTS.add("resource" + id); }
}
@Singleton class AShared {
    boolean closed;
    @PreDestroy void close() { closed = true; Log.EVENTS.add("shared"); }
}
'''

    void "resources resolved during and after construction belong to the consumer"() {
        given:
        def ctx = buildContext(HEADER + '''
@Singleton class ZOwner {
    final BeanDependencyResolver resolver;
    final Resource first;
    Resource second;
    final AShared shared;
    ZOwner(BeanDependencyResolver resolver) {
        this.resolver = resolver;
        first = resolver.getBean(Resource.class);
        shared = resolver.getBean(AShared.class);
    }
    void later() { second = resolver.getBean(Resource.class); }
    @PreDestroy void close() {
        if (shared.closed) throw new AssertionError("shared bean closed too soon");
        Log.EVENTS.add("owner");
    }
}
''')
        def owner = ctx.getBean(ctx.classLoader.loadClass('test.ZOwner'))
        def log = ctx.classLoader.loadClass('test.Log')
        owner.later()
        def resolver = owner.resolver

        expect:
        !owner.first.is(owner.second)

        when:
        ctx.close()

        then:
        log.EVENTS == ['owner', 'resource2', 'resource1', 'shared']

        when:
        resolver.getBean(String)

        then:
        thrown(IllegalStateException)
    }

    void "a failed owner destroys its resources and leaves shared beans alive"() {
        given:
        def ctx = buildContext(HEADER + '''
@Prototype class Owner {
    Owner(BeanDependencyResolver resolver) {
        resolver.getBean(Resource.class);
        resolver.getBean(AShared.class);
        throw new IllegalStateException("creation failed");
    }
}
''')
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        ctx.getBean(ctx.classLoader.loadClass('test.Owner'))

        then:
        thrown(BeanInstantiationException)
        log.EVENTS == ['resource1']
        !ctx.getBean(ctx.classLoader.loadClass('test.AShared')).closed

        cleanup:
        ctx.close()
    }

    void "a dependency reached through owned advice outlives all consumers"() {
        given:
        def ctx = buildContext(HEADER + '''
@Prototype class Advice {
    final AShared shared;
    Advice(BeanDependencyResolver resolver) { shared = resolver.getBean(AShared.class); }
    @PreDestroy void close() { Log.EVENTS.add("advice:" + shared.closed); }
}
@Singleton class YOwner {
    YOwner(Advice advice) { }
    @PreDestroy void close() { Log.EVENTS.add("Y"); }
}
@Singleton class ZOwner {
    ZOwner(Advice advice) { }
    @PreDestroy void close() { Log.EVENTS.add("Z"); }
}
''')
        ctx.getBean(ctx.classLoader.loadClass('test.YOwner'))
        ctx.getBean(ctx.classLoader.loadClass('test.ZOwner'))
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        ctx.close()

        then:
        log.EVENTS == ['Y', 'advice:false', 'Z', 'advice:false', 'shared']
    }

    void "prototype owners with the same definition keep separate resources"() {
        given:
        def ctx = buildContext(HEADER + '''
@Prototype class Owner {
    final Resource resource;
    Owner(BeanDependencyResolver resolver) { resource = resolver.getBean(Resource.class); }
}
''')
        def type = ctx.classLoader.loadClass('test.Owner')
        def first = ctx.getBeanRegistration(type, null)
        def second = ctx.getBeanRegistration(type, null)
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        first.close()
        first.close()

        then:
        log.EVENTS == ['resource1']

        when:
        second.close()

        then:
        log.EVENTS == ['resource1', 'resource2']

        cleanup:
        ctx.close()
    }

    void "a lookup racing destruction cleans up without publishing the dependency"() {
        given:
        def ctx = buildContext(HEADER + '''
@Prototype class SlowResource {
    static final CountDownLatch ENTERED = new CountDownLatch(1);
    static final CountDownLatch RELEASE = new CountDownLatch(1);
    SlowResource() throws InterruptedException { ENTERED.countDown(); RELEASE.await(); }
    @PreDestroy void close() { Log.EVENTS.add("slow"); }
}
@Singleton class Owner {
    final BeanDependencyResolver resolver;
    Owner(BeanDependencyResolver resolver) { this.resolver = resolver; }
}
''')
        def owner = ctx.getBean(ctx.classLoader.loadClass('test.Owner'))
        def slow = ctx.classLoader.loadClass('test.SlowResource')
        def log = ctx.classLoader.loadClass('test.Log')
        def lookup = CompletableFuture.supplyAsync { owner.resolver.getBean(slow) }
        assert slow.ENTERED.await(10, TimeUnit.SECONDS)

        when:
        ctx.destroyBean(owner)
        slow.RELEASE.countDown()
        lookup.get(10, TimeUnit.SECONDS)

        then:
        def failure = thrown(java.util.concurrent.ExecutionException)
        failure.cause instanceof IllegalStateException
        log.EVENTS == ['slow']

        cleanup:
        slow.RELEASE.countDown()
        ctx.close()
    }

    void "resolution stops before the owner's pre-destroy callback"() {
        given:
        def ctx = buildContext(HEADER + '''
@Singleton class Owner {
    final BeanDependencyResolver resolver;
    Owner(BeanDependencyResolver resolver) { this.resolver = resolver; }
    @PreDestroy void close() {
        try { resolver.getBean(Resource.class); }
        catch (IllegalStateException expected) { Log.EVENTS.add("rejected"); }
    }
}
''')
        def owner = ctx.getBean(ctx.classLoader.loadClass('test.Owner'))
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        ctx.destroyBean(owner)

        then:
        log.EVENTS == ['rejected']

        cleanup:
        ctx.close()
    }

    void "a resolver cannot be obtained without an owner"() {
        given:
        def ctx = buildContext(HEADER)
        when:
        ctx.getBean(BeanDependencyResolver)
        then:
        def e = thrown(io.micronaut.context.exceptions.BeanContextException)
        e.message.contains('must be injected')
        cleanup:
        ctx.close()
    }
    void "a failed lookup cleans nested resources without closing previous successful lookups"() {
        given:
        def ctx = buildContext(HEADER + '''
@Prototype class Broken {
    Broken(Resource resource) { throw new IllegalArgumentException("broken"); }
}
@Singleton class Owner {
    final BeanDependencyResolver resolver;
    Owner(BeanDependencyResolver resolver) { this.resolver = resolver; }
}
''')
        def resolver = ctx.getBean(ctx.classLoader.loadClass('test.Owner')).resolver
        def log = ctx.classLoader.loadClass('test.Log')
        resolver.getBean(ctx.classLoader.loadClass('test.Resource'))

        when:
        resolver.getBean(ctx.classLoader.loadClass('test.Broken'))

        then:
        thrown(BeanInstantiationException)
        log.EVENTS == ['resource2']

        when:
        ctx.close()

        then:
        log.EVENTS == ['resource2', 'resource1']
    }

    void "qualified factory products keep their nested dependents"() {
        given:
        def ctx = buildContext(HEADER + '''
class Product<T> {
    void close() { Log.EVENTS.add("product"); }
}
@Factory class Products {
    @Prototype @Named("chosen") @Bean(preDestroy = "close")
    Product<String> chosen(Resource resource) { return new Product<>(); }
    @Prototype @Named("other") Product<String> other() { throw new AssertionError("wrong qualifier"); }
}
@Singleton class Owner {
    Owner(BeanDependencyResolver resolver) {
        resolver.getBean(Argument.of(Product.class, String.class), Qualifiers.byName("chosen"));
    }
}
''')
        ctx.getBean(ctx.classLoader.loadClass('test.Owner'))
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        ctx.close()

        then:
        log.EVENTS == ['product', 'resource1']
    }

    void "a native prototype interceptor retains runtime dependencies for its target"() {
        given:
        def ctx = buildContext(HEADER + '''
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@io.micronaut.aop.Around
@interface Traced {}
@Prototype
@io.micronaut.aop.InterceptorBean(Traced.class)
class Advice implements io.micronaut.aop.MethodInterceptor<Object, Object> {
    final BeanDependencyResolver resolver;
    AShared shared;
    Advice(BeanDependencyResolver resolver) { this.resolver = resolver; }
    public Object intercept(io.micronaut.aop.MethodInvocationContext<Object, Object> invocation) {
        shared = resolver.getBean(AShared.class);
        resolver.getBean(Resource.class);
        return invocation.proceed();
    }
    @PreDestroy void close() { Log.EVENTS.add("advice:" + shared.closed); }
}
@Singleton @Traced class ZTarget {
    public void run() { }
    @PreDestroy void close() { Log.EVENTS.add("target"); }
}
''')
        def target = ctx.getBean(ctx.classLoader.loadClass('test.ZTarget'))
        def log = ctx.classLoader.loadClass('test.Log')
        target.run()

        when:
        ctx.close()

        then:
        log.EVENTS == ['target', 'advice:false', 'resource1', 'shared']
    }

    void "destruction listeners retain their runtime dependencies until their own destruction"() {
        given:
        def ctx = buildContext(HEADER + '''
@Singleton class ZListener implements io.micronaut.context.event.BeanDestroyedEventListener<Resource> {
    final AShared shared;
    ZListener(BeanDependencyResolver resolver) { shared = resolver.getBean(AShared.class); }
    public void onDestroyed(io.micronaut.context.event.BeanDestroyedEvent<Resource> event) {
        Log.EVENTS.add("listener:" + shared.closed);
    }
    @PreDestroy void close() { Log.EVENTS.add("listenerClosed:" + shared.closed); }
}
@Singleton class Owner {
    Owner(Resource resource) { }
}
''')
        ctx.getBean(ctx.classLoader.loadClass('test.Owner'))
        ctx.getBean(ctx.classLoader.loadClass('test.ZListener'))
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        ctx.close()

        then:
        log.EVENTS == ['resource1', 'listener:false', 'listenerClosed:false', 'shared']
    }

    void "runtime dependency cycles terminate and destroy each bean once"() {
        given:
        def ctx = buildContext(HEADER + '''
@Singleton class A {
    final BeanDependencyResolver resolver;
    A(BeanDependencyResolver resolver) { this.resolver = resolver; }
    @PreDestroy void close() { Log.EVENTS.add("A"); }
}
@Singleton class B {
    final BeanDependencyResolver resolver;
    B(BeanDependencyResolver resolver) { this.resolver = resolver; }
    @PreDestroy void close() { Log.EVENTS.add("B"); }
}
''')
        def aType = ctx.classLoader.loadClass('test.A')
        def bType = ctx.classLoader.loadClass('test.B')
        def a = ctx.getBean(aType)
        def b = ctx.getBean(bType)
        a.resolver.getBean(bType)
        b.resolver.getBean(aType)
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        ctx.close()

        then:
        log.EVENTS == ['A', 'B']
    }

    void "custom scoped dependencies remain owned by their scope"() {
        given:
        def ctx = buildContext(HEADER + '''
@Scope @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@interface SharedScope {}
@Singleton class TestScope extends io.micronaut.context.scope.AbstractConcurrentCustomScope<SharedScope> {
    final Map<io.micronaut.inject.BeanIdentifier, io.micronaut.context.scope.CreatedBean<?>> beans = new ConcurrentHashMap<>();
    TestScope() { super(SharedScope.class); }
    public boolean isRunning() { return true; }
    protected Map<io.micronaut.inject.BeanIdentifier, io.micronaut.context.scope.CreatedBean<?>> getScopeMap(boolean create) { return beans; }
    public void close() { destroyScope(beans); }
}
@Bean @SharedScope class Scoped {
    Scoped(Resource resource) { }
    @PreDestroy void close() { Log.EVENTS.add("scoped"); }
}
@Singleton class Owner {
    Owner(BeanDependencyResolver resolver) { resolver.getBean(Scoped.class); }
}
''')
        def owner = ctx.getBean(ctx.classLoader.loadClass('test.Owner'))
        def scope = ctx.getBean(ctx.classLoader.loadClass('test.TestScope'))
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        ctx.destroyBean(owner)

        then:
        log.EVENTS.empty
        scope.beans.size() == 1

        when:
        scope.close()

        then:
        log.EVENTS == ['scoped', 'resource1']

        cleanup:
        ctx.close()
    }

    void "shutdown inspects cached lazy targets without initializing unused targets (#used)"() {
        given:
        def ctx = buildContext(HEADER + '''
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@io.micronaut.aop.Around(proxyTarget = true, lazy = true, cacheableLazyTarget = true)
@interface Lazy {}
@Singleton @io.micronaut.aop.InterceptorBean(Lazy.class)
class Advice implements io.micronaut.aop.MethodInterceptor<Object, Object> {
    public Object intercept(io.micronaut.aop.MethodInvocationContext<Object, Object> invocation) {
        return invocation.proceed();
    }
}
@Prototype @Lazy class ZTarget {
    @Inject BeanDependencyResolver resolver;
    AShared shared;
    @PostConstruct void init() {
        shared = resolver.getBean(AShared.class);
        resolver.getBean(Resource.class);
    }
    public void run() { }
    @PreDestroy void close() { Log.EVENTS.add("target:" + shared.closed); }
}
@Singleton class Owner {
    final ZTarget target;
    Owner(BeanDependencyResolver resolver) { target = resolver.getBean(ZTarget.class); }
}
''')
        def owner = ctx.getBean(ctx.classLoader.loadClass('test.Owner'))
        def log = ctx.classLoader.loadClass('test.Log')
        if (used) owner.target.run()

        when:
        ctx.close()

        then:
        log.EVENTS == (used ? ['target:false', 'resource1', 'shared'] : [])

        where:
        used << [false, true]
    }

}

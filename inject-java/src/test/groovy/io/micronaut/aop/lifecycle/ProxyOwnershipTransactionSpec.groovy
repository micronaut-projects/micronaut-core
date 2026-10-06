package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.proxy.InterceptedBeanProxy
import spock.lang.Unroll
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit


class ProxyOwnershipTransactionSpec extends AbstractTypeElementSpec {
    private static final String HEADER = '''
package test;
import io.micronaut.aop.*;
import io.micronaut.context.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;
import jakarta.annotation.*;
import java.util.*;
import java.util.concurrent.*;
class Log { static final List<String> events = new CopyOnWriteArrayList<>(); static int resources; }
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Around(proxyTarget = true, lazy = true, cacheableLazyTarget = true, lazyInterceptorsPerTarget = PER_TARGET)
@interface Lazy {}
@Prototype @InterceptorBean(Lazy.class)
class Advice implements MethodInterceptor<Object, Object> {
    public Object intercept(MethodInvocationContext<Object, Object> context) { return context.proceed(); }
    @PreDestroy void stop() { Log.events.add("advice"); }
}
@Prototype class Resource {
    Resource() { Log.resources++; }
    @PreDestroy void stop() { Log.events.add("resource"); }
}
'''

    @Unroll
    void "cached targets retain original registrations regardless of interceptor ownership #perTarget"() {
        given:
        def ctx = buildContext(HEADER.replace('PER_TARGET', perTarget.toString()) + '''
@Prototype @Lazy class Target {
    final Resource resource;
    Target(Resource resource) { this.resource = resource; }
    public String run() { return "ok"; }
    @PreDestroy void stop() { Log.events.add("target"); }
}
''')
        def type = ctx.classLoader.loadClass('test.Target')
        def log = ctx.classLoader.loadClass('test.Log')
        def proxy = ctx.getBean(type) as InterceptedBeanProxy

        expect:
        !proxy.hasCachedInterceptedTarget()
        proxy.interceptedTargetRegistration() == null
        proxy.run() == 'ok'
        proxy.interceptedTargetRegistration().bean().is(proxy.interceptedTarget())

        when:
        ctx.destroyBean(proxy)

        then:
        log.events.count('target') == 1
        log.events.count('resource') == log.resources
        log.events.count('advice') == 1
        log.events.indexOf('target') < log.events.indexOf('advice')

        when:
        proxy.interceptedTarget()

        then:
        thrown(IllegalStateException)
        !proxy.hasCachedInterceptedTarget()

        cleanup:
        ctx.close()

        where:
        perTarget << [false, true]
    }

    @Unroll
    void "destroying an unused proxy prevents later target creation #perTarget"() {
        given:
        def ctx = buildContext(HEADER.replace('PER_TARGET', perTarget.toString()) + '''
@Prototype @Lazy class Target {
    Target(Resource resource) { }
    public String run() { return "ok"; }
    @PreDestroy void stop() { Log.events.add("target"); }
}
''')
        def type = ctx.classLoader.loadClass('test.Target')
        def log = ctx.classLoader.loadClass('test.Log')
        def proxy = ctx.getBean(type) as InterceptedBeanProxy

        when:
        ctx.destroyBean(proxy)
        proxy.interceptedTarget()

        then:
        thrown(IllegalStateException)
        !proxy.hasCachedInterceptedTarget()
        log.events.count('target') == 0

        cleanup:
        ctx.close()

        where:
        perTarget << [false, true]
    }
    @Unroll
    void "target creation racing proxy destruction rolls back its complete registration #perTarget"() {
        given:
        def ctx = buildContext(HEADER.replace('PER_TARGET', perTarget.toString()) + """
@Prototype @Lazy class Target {
    static final CountDownLatch entered = new CountDownLatch(1);
    static final CountDownLatch proceed = new CountDownLatch(1);
    Target(Resource resource) { }
    @PostConstruct void init() throws Exception { entered.countDown(); proceed.await(); }
    public String run() { return "ok"; }
    @PreDestroy void stop() { Log.events.add("target"); }
}
""")
        def type = ctx.classLoader.loadClass('test.Target')
        def log = ctx.classLoader.loadClass('test.Log')
        def proxy = ctx.getBean(type) as InterceptedBeanProxy
        def result = CompletableFuture.supplyAsync { proxy.interceptedTarget() }
        assert type.entered.await(10, TimeUnit.SECONDS)

        when:
        ctx.destroyBean(proxy)
        type.proceed.countDown()
        result.get(10, TimeUnit.SECONDS)

        then:
        def failure = thrown(java.util.concurrent.ExecutionException)
        failure.cause instanceof IllegalStateException
        !proxy.hasCachedInterceptedTarget()
        log.events.count('target') == 1
        log.events.count('resource') == log.resources

        cleanup:
        type.proceed.countDown()
        ctx.close()

        where:
        perTarget << [false, true]
    }

    void "destruction between resolution and cache publication cannot retain a target #perTarget"() {
        given:
        def ctx = buildContext(HEADER.replace('PER_TARGET', perTarget.toString())
            .replace('@Prototype @InterceptorBean', '@Singleton @InterceptorBean') + '''
@Prototype @Lazy class Target {
    public String run() { return "ok"; }
    @PreDestroy void stop() { Log.events.add("target"); }
}
''')
        def type = ctx.classLoader.loadClass('test.Target')
        def log = ctx.classLoader.loadClass('test.Log')
        def definition = ctx.getBeanDefinition(type)
        def group = ctx.createDependencyGroup()
        def delegate = new io.micronaut.context.DefaultBeanResolutionContext(ctx, definition)
        io.micronaut.context.BeanResolutionContext resolution
        resolution = java.lang.reflect.Proxy.newProxyInstance(getClass().classLoader,
            [io.micronaut.context.BeanResolutionContext] as Class[], { ignored, method, arguments ->
                switch (method.name) {
                    case 'getBeanDependencyGroup': return group
                    case 'copyForLazyProxyTarget': return resolution
                    case 'getProxyTargetBeanRegistration':
                        def target = group.createBeanRegistration(arguments[0])
                        // Force the exact interval after Core returns an owned target but before the proxy caches it.
                        group.close()
                        return target
                    default:
                        try {
                            return method.invoke(delegate, arguments)
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.cause
                        }
                }
            } as java.lang.reflect.InvocationHandler) as io.micronaut.context.BeanResolutionContext
        def proxy = definition.instantiate(resolution, ctx) as InterceptedBeanProxy

        when:
        proxy.interceptedTarget()

        then:
        thrown(IllegalStateException)
        !proxy.hasCachedInterceptedTarget()
        proxy.interceptedTargetRegistration() == null
        log.events.count('target') == 1

        cleanup:
        group.close()
        delegate.close()
        ctx.close()

        where:
        perTarget << [false, true]
    }

    void "registration ownership freezes proxy dependencies before callbacks - #wrapper perTarget #perTarget"() {
        given:
        def ctx = buildContext(HEADER.replace('PER_TARGET', perTarget.toString())
            .replace('static int resources;', 'static int resources; static BeanDependencyResolver proxyResolver;') + '''
@Prototype @Lazy class Target {
    Target(BeanDependencyResolver resolver) {
        if (Log.proxyResolver == null) { Log.proxyResolver = resolver; }
    }
    public String run() { return "ok"; }
    @PreDestroy void stop() {
        try {
            Log.proxyResolver.getBean(Resource.class);
            Log.events.add("accepted");
        } catch (IllegalStateException expected) {
            Log.events.add("rejected");
        }
        Log.events.add("target");
    }
}
''')
        def type = ctx.classLoader.loadClass('test.Target')
        def log = ctx.classLoader.loadClass('test.Log')
        def original = ctx.getBeanRegistration(type, null)
        def proxy = original.bean()
        assert proxy.run() == 'ok'
        log.proxyResolver.getBean(ctx.classLoader.loadClass('test.Resource'))
        def owned = original.dependentBeans()
        def registration = switch (wrapper) {
            case 'plain' -> new io.micronaut.context.BeanRegistration(original.id(), original.definition(), proxy)
            case 'disposing' -> io.micronaut.context.BeanRegistration.of(ctx, original.id(), original.definition(), proxy)
            default -> original
        }

        expect:
        registration.dependentBeans().size() == owned.size()
        registration.dependentBeans().every { candidate -> owned.any { it.is(candidate) } }

        when:
        if (wrapper == 'instance') {
            ctx.destroyBean(proxy)
        } else {
            ctx.destroyDependentBean(registration)
        }
        original.close()
        def closedWrapper = io.micronaut.context.BeanRegistration.of(ctx, original.id(), original.definition(), proxy)
        closedWrapper.close()

        then:
        log.events.count('rejected') == 1
        !log.events.contains('accepted')
        log.events.count('target') == 1
        log.events.count('resource') == 1
        log.events.count('advice') == 1
        log.events.indexOf('target') < log.events.indexOf('advice')
        original.dependentBeans().isEmpty()
        registration.dependentBeans().isEmpty()
        closedWrapper.dependentBeans().isEmpty()

        cleanup:
        ctx.close()

        where:
        [wrapper, perTarget] << [['original', 'plain', 'disposing', 'instance'], [false, true]].combinations()
    }

}

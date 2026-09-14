package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import io.micronaut.runtime.context.scope.refresh.RefreshScope

import io.micronaut.aop.HotSwappableInterceptedProxy
import io.micronaut.aop.InterceptedProxy

import java.util.concurrent.CountDownLatch

/**
 * A proxy that fronts a separate target takes the non-singleton interceptors of a call from the dependents of the
 * target's registration, which is where the interceptors created with the target live, and creates as a further
 * dependent of the target any bound only for {@code AROUND}. The proxy itself is injected with singletons only. So
 * the instance that intercepted a target's {@code POST_CONSTRUCT} intercepts its methods through the proxy and its
 * {@code PRE_DESTROY}, and is destroyed with the target, whether the proxy fronts one target for its life or a
 * different one on each call.
 */
class ProxyTargetInterceptorsFromDependentsSpec extends AbstractTypeElementSpec {

    private static final String PROBED = '''
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.AROUND)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Probed {
}

class Events {
    static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());
    // target -> kind -> the interceptor instance that intercepted that kind of that target
    static final Map<Object, Map<String, Object>> SEEN = Collections.synchronizedMap(new IdentityHashMap<>());
    static final AtomicInteger INTERCEPTORS = new AtomicInteger();
    static final AtomicInteger TARGETS = new AtomicInteger();

    static void seen(Object target, String kind, Object interceptor) {
        SEEN.computeIfAbsent(target, t -> Collections.synchronizedMap(new LinkedHashMap<>())).put(kind, interceptor);
    }
}

@Prototype
@InterceptorBinding(value = Probed.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Probed.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Probed.class, kind = InterceptorKind.PRE_DESTROY)
class ProbingInterceptor implements MethodInterceptor<Object, Object> {
    public final int id = Events.INTERCEPTORS.incrementAndGet();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        MyBean target = (MyBean) context.getTarget();
        Events.seen(target, context.getKind().name(), this);
        Events.LOG.add(id + ":" + context.getKind() + ":" + target.name);
        return context.proceed();
    }

    @PreDestroy
    void destroy() {
        Events.LOG.add(id + ":DESTROYED");
    }
}
'''

    /**
     * The interceptor instance that intercepted each kind, by the name of the target it intercepted.
     */
    private static Map<String, Map<String, Object>> byTargetName(Map<Object, Map<String, Object>> seen) {
        seen.collectEntries { target, byKind -> [(target.name): byKind] }
    }

    /**
     * The position of an entry in the log, or -1. Takes a String so that a GString argument is converted, which
     * List#indexOf would not do.
     */
    private static int at(List<String> log, String entry) {
        log.indexOf(entry)
    }

    /**
     * For each target, the positions in the log of: the pre destroy interception by the instance that intercepted
     * its methods, the target's own pre destroy callback, and the destruction of that instance.
     */
    private static List<List<Integer>> destruction(List<String> log, Map<String, Map<String, Object>> seen, List<String> targets) {
        targets.collect { String target ->
            int id = seen[target]['AROUND'].id
            [at(log, "$id:PRE_DESTROY:$target"), at(log, "$target:CLOSED"), at(log, "$id:DESTROYED")]
        }
    }

    private static boolean inOrder(List<Integer> positions) {
        positions[0] != -1 && positions[0] < positions[1] && positions[1] < positions[2]
    }

    private static String source(String scope) {
        """
package scoped;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

$PROBED

$scope
@Probed
class MyBean {
    final String name = "target" + Events.TARGETS.incrementAndGet();

    @PostConstruct void init() {}

    String work() { return name; }

    @PreDestroy void close() {
        Events.LOG.add(name + ":CLOSED");
    }
}
"""
    }

    void 'test a thread local scoped proxy applies the interceptor resolved for the target of the current thread'() {
        given:
        ApplicationContext context = buildContext(
                'scoped.MyBean',
                source('@io.micronaut.runtime.context.scope.ThreadLocal(lifecycle = true)'),
                true
        )
        def events = context.classLoader.loadClass('scoped.Events')
        def proxy = context.getBean(context.classLoader.loadClass('scoped.MyBean'))

        when: 'the bean is called twice on this thread and once on another, which stays alive until the context stops'
        def onThisThread = proxy.work()
        proxy.work()
        def onOtherThread = null
        def release = new CountDownLatch(1)
        def other = new Thread({
            onOtherThread = proxy.work()
            release.await()
        })
        other.start()
        while (onOtherThread == null) {
            Thread.sleep(10)
        }
        Map<String, Map<String, Object>> seen = byTargetName(events.SEEN)

        then: 'each thread had its own target'
        onThisThread != onOtherThread
        seen.size() == 2

        and: 'for each target, post construct and every method call used the instance resolved for that target'
        seen.values().every { it['POST_CONSTRUCT'].is(it['AROUND']) }

        and: 'the two targets did not share an instance'
        !seen.values()[0]['AROUND'].is(seen.values()[1]['AROUND'])

        when: 'the context stops, which destroys the target of each thread'
        context.stop()
        release.countDown()
        other.join()
        List<String> log = events.LOG

        then: 'pre destroy used the instance of the target being destroyed'
        seen.values().every { it['PRE_DESTROY'].is(it['AROUND']) }

        and: 'each instance was destroyed as a dependent of its target, after the target\'s own pre destroy'
        destruction(log, seen, [onThisThread, onOtherThread]).every { inOrder(it) }

        cleanup:
        context.close()
    }

    void 'test a refreshable proxy applies the interceptor of the current target and destroys it with the target'() {
        given:
        ApplicationContext context = buildContext(
                'scoped.MyBean',
                source('@io.micronaut.runtime.context.scope.Refreshable'),
                true
        )
        def events = context.classLoader.loadClass('scoped.Events')
        def proxy = context.getBean(context.classLoader.loadClass('scoped.MyBean'))

        when:
        def first = proxy.work()
        proxy.work()
        context.getBean(RefreshScope).onRefreshEvent(new RefreshEvent())
        def second = proxy.work()
        Map<String, Map<String, Object>> seen = byTargetName(events.SEEN)
        List<String> log = events.LOG

        then: 'the refresh replaced the target'
        first != second
        seen.size() == 2

        and: 'the first target was intercepted by one instance in every phase, and that instance died with it'
        def firstTarget = seen[first]
        firstTarget['POST_CONSTRUCT'].is(firstTarget['AROUND'])
        firstTarget['PRE_DESTROY'].is(firstTarget['AROUND'])
        at(log, "${firstTarget['AROUND'].id}:PRE_DESTROY:$first") != -1
        at(log, "${firstTarget['AROUND'].id}:PRE_DESTROY:$first") < at(log, "$first:CLOSED")
        at(log, "$first:CLOSED") < at(log, "${firstTarget['AROUND'].id}:DESTROYED")

        and: 'the second target has an instance of its own'
        def secondTarget = seen[second]
        secondTarget['POST_CONSTRUCT'].is(secondTarget['AROUND'])
        !secondTarget['AROUND'].is(firstTarget['AROUND'])
        at(log, "${secondTarget['AROUND'].id}:DESTROYED") == -1

        cleanup:
        context.close()
    }

    void 'test a prototype proxy target is intercepted by the instance resolved for it'() {
        given:
        ApplicationContext context = buildContext('scoped.MyBean', '''
package scoped;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
''' + PROBED + '''

@Prototype
@Around(proxyTarget = true)
@Probed
class MyBean {
    final String name = "target" + Events.TARGETS.incrementAndGet();

    @PostConstruct void init() {}

    String work() { return name; }

    @PreDestroy void close() {
        Events.LOG.add(name + ":CLOSED");
    }
}

@Singleton
class Holder {
    @Inject MyBean first;
    @Inject MyBean second;
}
''', true)
        def events = context.classLoader.loadClass('scoped.Events')
        def holder = context.getBean(context.classLoader.loadClass('scoped.Holder'))

        when:
        def first = holder.first.work()
        def second = holder.second.work()
        Map<String, Map<String, Object>> seen = byTargetName(events.SEEN)

        then: 'each proxy fronts its own target, intercepted in every phase by the instance resolved for that target'
        first != second
        seen.size() == 2
        seen.values().every { it['POST_CONSTRUCT'].is(it['AROUND']) }
        !seen.values()[0]['AROUND'].is(seen.values()[1]['AROUND'])

        when:
        context.stop()
        List<String> log = events.LOG

        then: 'each instance handled pre destroy of its target and was then destroyed with it'
        seen.values().every { it['PRE_DESTROY'].is(it['AROUND']) }
        destruction(log, seen, [first, second]).every { inOrder(it) }

        cleanup:
        context.close()
    }

    void 'test a factory produced prototype proxy target is intercepted by the instance resolved for it'() {
        given:
        ApplicationContext context = buildContext('scoped.MyBean', '''
package scoped;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
''' + PROBED + '''

class MyBean {
    final String name = "target" + Events.TARGETS.incrementAndGet();

    // a factory produced bean is advised on its public methods only
    public String work() { return name; }

    // and its pre destroy callback is the one the factory names
    void close() {
        Events.LOG.add(name + ":CLOSED");
    }
}

@Factory
class MyBeanFactory {
    @Prototype
    @Bean(preDestroy = "close")
    @Around(proxyTarget = true)
    @Probed
    MyBean myBean() {
        return new MyBean();
    }
}

@Singleton
class Holder {
    @Inject MyBean first;
    @Inject MyBean second;
}
''', true)
        def events = context.classLoader.loadClass('scoped.Events')
        def holder = context.getBean(context.classLoader.loadClass('scoped.Holder'))

        when:
        def first = holder.first.work()
        def second = holder.second.work()
        Map<String, Map<String, Object>> seen = byTargetName(events.SEEN)

        then: 'each proxy fronts its own target, intercepted in every phase by the instance resolved for that target'
        first != second
        seen.size() == 2
        seen.values().every { it['POST_CONSTRUCT'].is(it['AROUND']) }
        !seen.values()[0]['AROUND'].is(seen.values()[1]['AROUND'])

        when:
        context.stop()
        List<String> log = events.LOG

        then: 'each instance handled pre destroy of its target and was then destroyed with it'
        seen.values().every { it['PRE_DESTROY'].is(it['AROUND']) }
        destruction(log, seen, [first, second]).every { inOrder(it) }

        cleanup:
        context.close()
    }

    // The target of a lazy proxy that caches it is resolved on the first call rather than by the constructor, and
    // kept from then on, together with its registration, through which the context destroys it and the beans
    // created with it when the proxy is destroyed.
    void 'test a cached lazy proxy target is intercepted by the instance resolved for it'() {
        given:
        ApplicationContext context = buildContext('scoped.MyBean', '''
package scoped;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
''' + PROBED + '''

@Prototype
@Around(proxyTarget = true, lazy = true, cacheableLazyTarget = true)
@Probed
class MyBean {
    final String name = "target" + Events.TARGETS.incrementAndGet();

    @PostConstruct void init() {}

    String work() { return name; }

    @PreDestroy void close() {
        Events.LOG.add(name + ":CLOSED");
    }
}

@Singleton
class Holder {
    @Inject MyBean first;
    @Inject MyBean second;
}
''', true)
        def events = context.classLoader.loadClass('scoped.Events')
        def holder = context.getBean(context.classLoader.loadClass('scoped.Holder'))

        when:
        def first = holder.first.work()
        holder.first.work()
        def second = holder.second.work()
        Map<String, Map<String, Object>> seen = byTargetName(events.SEEN)
        List<String> log = events.LOG

        then: 'each proxy resolved one target on its first call and keeps it'
        first != second
        holder.first.work() == first
        seen.size() == 2

        and: 'every call on a target is intercepted by the instance that ran its post construct'
        seen.values().every { it['POST_CONSTRUCT'].is(it['AROUND']) }
        !seen.values()[0]['AROUND'].is(seen.values()[1]['AROUND'])
        log.count { it.endsWith(":AROUND:$first") } == 3
        log.findAll { it.endsWith(":AROUND:$first") }.toSet().size() == 1

        when:
        context.stop()
        log = new ArrayList<>(events.LOG)

        then: 'each instance handled pre destroy of its target and was then destroyed with it'
        seen.values().every { it['PRE_DESTROY'].is(it['AROUND']) }
        destruction(log, seen, [first, second]).every { inOrder(it) }

        cleanup:
        context.close()
    }

    void 'test a hot swapped proxy no longer intercepts the new target with the instance of the old one'() {
        given:
        ApplicationContext context = buildContext('scoped.MyBean', '''
package scoped;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
''' + PROBED + '''

@Prototype
@Around(proxyTarget = true, hotswap = true)
@Probed
class MyBean {
    final String name = "target" + Events.TARGETS.incrementAndGet();

    @PostConstruct void init() {}

    String work() { return name; }

    @PreDestroy void close() {
        Events.LOG.add(name + ":CLOSED");
    }
}

@Singleton
class Holder {
    @Inject MyBean first;
    @Inject MyBean second;
}
''', true)
        def events = context.classLoader.loadClass('scoped.Events')
        def holder = context.getBean(context.classLoader.loadClass('scoped.Holder'))

        when:
        def first = holder.first.work()
        def second = holder.second.work()
        Map<String, Map<String, Object>> seen = byTargetName(events.SEEN)
        def firstInstance = seen[first]['AROUND']
        def secondInstance = seen[second]['AROUND']

        then: 'before the swap each proxy intercepts its own target with the instance resolved for that target'
        first != second
        seen[first]['POST_CONSTRUCT'].is(firstInstance)
        seen[second]['POST_CONSTRUCT'].is(secondInstance)
        !firstInstance.is(secondInstance)

        when: 'the first proxy is given the target of the second'
        def swappedOut = ((HotSwappableInterceptedProxy) holder.first).swap(((InterceptedProxy) holder.second).interceptedTarget())
        def afterSwap = holder.first.work()
        List<String> log = new ArrayList<>(events.LOG)
        String lastAround = log.reverse().find { it.contains(':AROUND:') }

        then: 'the call reached the new target'
        swappedOut.name == first
        afterSwap == second

        and: 'the instance of the old target did not intercept it'
        lastAround.endsWith(':AROUND:' + second)
        !lastAround.startsWith(firstInstance.id + ':')

        cleanup:
        context.close()
    }

    void 'test a method bound to a further interceptor keeps it when the class binds a prototype one'() {
        given:
        ApplicationContext context = buildContext('scoped.MyBean', '''
package scoped;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
''' + PROBED + '''

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.AROUND)
@interface Counted {
}

@Singleton
@InterceptorBinding(value = Counted.class, kind = InterceptorKind.AROUND)
class CountingInterceptor implements MethodInterceptor<Object, Object> {
    static final AtomicInteger CALLS = new AtomicInteger();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        CALLS.incrementAndGet();
        return context.proceed();
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.AROUND)
@interface Traced {
}

// bound for AROUND only, so it is not created with the target: the proxy creates it for the target on first use
@Prototype
@InterceptorBinding(value = Traced.class, kind = InterceptorKind.AROUND)
class TracingInterceptor implements MethodInterceptor<Object, Object> {
    static final AtomicInteger INSTANCES = new AtomicInteger();
    public final int id = INSTANCES.incrementAndGet();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Events.LOG.add("T" + id + ":AROUND:" + ((MyBean) context.getTarget()).name);
        return context.proceed();
    }

    @PreDestroy
    void destroy() {
        Events.LOG.add("T" + id + ":DESTROYED");
    }
}

@Prototype
@Around(proxyTarget = true)
@Probed
class MyBean {
    final String name = "target" + Events.TARGETS.incrementAndGet();

    @PostConstruct void init() {}

    String work() { return name; }

    @Counted String counted() { return name; }

    @Traced String traced() { return name; }

    @PreDestroy void close() {
        Events.LOG.add(name + ":CLOSED");
    }
}

@Singleton
class Holder {
    @Inject MyBean bean;
}
''', true)
        def events = context.classLoader.loadClass('scoped.Events')
        def counting = context.classLoader.loadClass('scoped.CountingInterceptor')
        def tracing = context.classLoader.loadClass('scoped.TracingInterceptor')
        def holder = context.getBean(context.classLoader.loadClass('scoped.Holder'))

        when:
        def name = holder.bean.counted()
        holder.bean.traced()
        holder.bean.traced()
        Map<String, Map<String, Object>> seen = byTargetName(events.SEEN)
        List<String> log = new ArrayList<>(events.LOG)

        then: 'the singleton bound on the method alone still intercepts it'
        counting.CALLS.get() == 1

        and: 'so does the prototype the class binds, with the instance resolved for the target'
        seen[name]['POST_CONSTRUCT'].is(seen[name]['AROUND'])

        and: 'the prototype bound on a method alone is one instance for the target, created on first use'
        tracing.INSTANCES.get() == 1
        log.count { it == 'T1:AROUND:' + name } == 2

        when:
        context.stop()
        log = new ArrayList<>(events.LOG)

        then: 'both prototypes are destroyed with the target, after its own pre destroy'
        at(log, name + ':CLOSED') != -1
        at(log, name + ':CLOSED') < at(log, 'T1:DESTROYED')
        inOrder(destruction(log, seen, [name])[0])

        cleanup:
        context.close()
    }

    void 'test a singleton proxy target is intercepted in every phase by the instance resolved for it'() {
        given:
        ApplicationContext context = buildContext('scoped.MyBean', '''
package scoped;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
''' + PROBED + '''

@Singleton
@Around(proxyTarget = true)
@Probed
class MyBean {
    final String name = "target" + Events.TARGETS.incrementAndGet();

    @PostConstruct void init() {}

    String work() { return name; }

    @PreDestroy void close() {
        Events.LOG.add(name + ":CLOSED");
    }
}
''', true)
        def events = context.classLoader.loadClass('scoped.Events')
        def proxy = context.getBean(context.classLoader.loadClass('scoped.MyBean'))

        when:
        def name = proxy.work()
        proxy.work()
        Map<String, Map<String, Object>> seen = byTargetName(events.SEEN)

        then: 'post construct and the method calls used one instance'
        seen.size() == 1
        seen[name]['POST_CONSTRUCT'].is(seen[name]['AROUND'])
        events.INTERCEPTORS.get() == 1

        when:
        context.stop()
        List<String> log = new ArrayList<>(events.LOG)

        then: 'and so did pre destroy, after which the instance was destroyed'
        seen[name]['PRE_DESTROY'].is(seen[name]['AROUND'])
        inOrder(destruction(log, seen, [name])[0])

        cleanup:
        context.close()
    }
}

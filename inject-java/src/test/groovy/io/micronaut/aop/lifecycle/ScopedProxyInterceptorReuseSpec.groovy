package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * A scoped proxy stands for many targets, one per scope instance, so a non-singleton interceptor has to be one
 * instance per target rather than one per proxy.
 */
class ScopedProxyInterceptorReuseSpec extends AbstractTypeElementSpec {

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

    void 'test a prototype interceptor is one instance per target of a scoped proxy'() {
        given:
        ApplicationContext context = buildContext("""
package scopedproxy.lifecycle;
$IMPORTS
$SCOPE

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Probed {
}

@Prototype
@InterceptorBean(Probed.class)
class ProbeInterceptor implements MethodInterceptor<Object, Object> {
    static int instances;
    static final List<String> events = new ArrayList<>();

    private final int id = ++instances;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        events.add(id + ":" + context.getKind() + ":" + ((ConversationBean) context.getTarget()).id);
        return context.proceed();
    }

    @PreDestroy
    void destroy() {
        events.add(id + ":DESTROYED");
    }
}

@Conversation
@Probed
class ConversationBean {
    static int instances;
    // the generated proxy extends this class, so only count the targets
    final int id = getClass() == ConversationBean.class ? ++instances : 0;

    @PostConstruct
    void init() {
    }

    public String call() {
        return "called " + id;
    }

    @PreDestroy
    void close() {
        ProbeInterceptor.events.add("target " + id + ":PRE_DESTROY_CALLBACK");
    }
}
""")
        Class<?> interceptorType = context.classLoader.loadClass('scopedproxy.lifecycle.ProbeInterceptor')
        Class<?> scopeType = context.classLoader.loadClass('scopedproxy.lifecycle.ConversationScope')
        def scope = context.getBean(scopeType)
        def bean = context.getBean(context.classLoader.loadClass('scopedproxy.lifecycle.ConversationBean'))

        when: 'the first conversation calls the bean twice'
        bean.call()
        bean.call()
        def perTarget = interceptorsPerTarget(interceptorType.events)

        then: 'one interceptor instance serves post construct and every method call of the first target'
        perTarget.keySet() == [1] as Set
        perTarget[1].size() == 1
        interceptorType.events.toList().collect { it.split(':')[1] } == ['POST_CONSTRUCT', 'AROUND', 'AROUND']

        when: 'a second conversation calls the same proxy'
        scopeType.current = 'second'
        bean.call()
        perTarget = interceptorsPerTarget(interceptorType.events)

        then: 'the second target gets its own instance for post construct and its method calls'
        perTarget.keySet() == [1, 2] as Set
        perTarget[1].size() == 1
        perTarget[2].size() == 1
        perTarget[1] != perTarget[2]
        interceptorType.events.toList()[3..4].collect { it.split(':')[1] } == ['POST_CONSTRUCT', 'AROUND']

        when: 'the first conversation ends'
        int first = perTarget[1].first()
        int second = perTarget[2].first()
        interceptorType.events.clear()
        scope.end('first')

        then: 'its instance intercepts pre destroy and is destroyed after the target, and only that one'
        interceptorType.events.toList() == [
                "$first:PRE_DESTROY:1".toString(),
                'target 1:PRE_DESTROY_CALLBACK',
                "$first:DESTROYED".toString()
        ]

        when: 'the second conversation calls again and then ends'
        interceptorType.events.clear()
        bean.call()
        scope.end('second')

        then: 'the second target still uses and then destroys its own instance'
        interceptorType.events.toList() == [
                "$second:AROUND:2".toString(),
                "$second:PRE_DESTROY:2".toString(),
                'target 2:PRE_DESTROY_CALLBACK',
                "$second:DESTROYED".toString()
        ]

        cleanup:
        scopeType.current = 'first'
        context.close()
    }

    void 'test an around only prototype interceptor is one instance per target of a scoped proxy'() {
        given:
        ApplicationContext context = buildContext("""
package scopedproxy.around;
$IMPORTS
$SCOPE

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Counted {
}

@Prototype
@InterceptorBean(Counted.class)
class CountingInterceptor implements MethodInterceptor<Object, Object> {
    static int instances;
    static final List<String> events = new ArrayList<>();

    private final int id = ++instances;
    private int count;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        events.add(id + ":" + (++count) + ":" + ((CountedBean) context.getTarget()).id);
        return context.proceed();
    }

    @PreDestroy
    void destroy() {
        events.add(id + ":DESTROYED");
    }
}

@Conversation
@Counted
class CountedBean {
    static int instances;
    // the generated proxy extends this class, so only count the targets
    final int id = getClass() == CountedBean.class ? ++instances : 0;

    public String call() {
        return "called " + id;
    }
}
""")
        Class<?> interceptorType = context.classLoader.loadClass('scopedproxy.around.CountingInterceptor')
        Class<?> scopeType = context.classLoader.loadClass('scopedproxy.around.ConversationScope')
        def scope = context.getBean(scopeType)
        def bean = context.getBean(context.classLoader.loadClass('scopedproxy.around.CountedBean'))

        when: 'two conversations call the same proxy'
        bean.call()
        bean.call()
        scopeType.current = 'second'
        bean.call()
        def perTarget = interceptorsPerTarget(interceptorType.events)

        then: 'each target has its own interceptor, whose state starts over'
        perTarget.keySet() == [1, 2] as Set
        perTarget[1].size() == 1
        perTarget[2].size() == 1
        perTarget[1] != perTarget[2]
        interceptorType.events.toList().collect { it.split(':')[1] } == ['1', '2', '1']

        when: 'the first conversation ends'
        interceptorType.events.clear()
        scope.end('first')

        then: 'only the first target interceptor is destroyed'
        interceptorType.events.toList() == ["${perTarget[1].first()}:DESTROYED".toString()]

        cleanup:
        scopeType.current = 'first'
        context.close()
    }

    void 'test a singleton interceptor of a scoped proxy is still shared by every target'() {
        given:
        ApplicationContext context = buildContext("""
package scopedproxy.singleton;
$IMPORTS
$SCOPE

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Shared {
}

@Singleton
@InterceptorBean(Shared.class)
class SharedInterceptor implements MethodInterceptor<Object, Object> {
    static int instances;
    static final Set<Integer> seen = new LinkedHashSet<>();

    private final int id = ++instances;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        seen.add(id);
        return context.proceed();
    }
}

@Conversation
@Shared
class SharedBean {
    @PostConstruct
    void init() {
    }

    public String call() {
        return "called";
    }
}
""")
        Class<?> interceptorType = context.classLoader.loadClass('scopedproxy.singleton.SharedInterceptor')
        Class<?> scopeType = context.classLoader.loadClass('scopedproxy.singleton.ConversationScope')
        def scope = context.getBean(scopeType)
        def bean = context.getBean(context.classLoader.loadClass('scopedproxy.singleton.SharedBean'))

        when:
        bean.call()
        scopeType.current = 'second'
        bean.call()
        scope.end('first')
        scope.end('second')
        bean.call()

        then:
        interceptorType.instances == 1
        interceptorType.seen == [1] as Set

        cleanup:
        scopeType.current = 'first'
        context.close()
    }

    void 'test a prototype interceptor is one instance for a singleton proxy target with lazy #lazy'() {
        given:
        ApplicationContext context = buildContext("""
package scopedproxy.singletontarget$lazy;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around(proxyTarget = true, lazy = $lazy)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Probed {
}

@Prototype
@InterceptorBean(Probed.class)
class ProbeInterceptor implements MethodInterceptor<Object, Object> {
    static final List<String> events = new ArrayList<>();
    static final Set<ProbeInterceptor> used = new LinkedHashSet<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        used.add(this);
        events.add(context.getKind().name());
        return context.proceed();
    }

    @PreDestroy
    void destroy() {
        if (used.contains(this)) {
            events.add("DESTROYED");
        }
    }
}

@Singleton
@Probed
class TargetBean {
    @PostConstruct
    void init() {
    }

    public String call() {
        return "called";
    }

    @PreDestroy
    void close() {
        ProbeInterceptor.events.add("PRE_DESTROY_CALLBACK");
    }
}
""")
        Class<?> interceptorType = context.classLoader.loadClass("scopedproxy.singletontarget${lazy}.ProbeInterceptor")
        def bean = context.getBean(context.classLoader.loadClass("scopedproxy.singletontarget${lazy}.TargetBean"))

        when:
        bean.call()
        bean.call()
        context.stop()

        then: 'post construct, the method calls and pre destroy share one instance, destroyed after the target'
        interceptorType.used.size() == 1
        interceptorType.events.toList() == [
                'POST_CONSTRUCT',
                'AROUND',
                'AROUND',
                'PRE_DESTROY',
                'PRE_DESTROY_CALLBACK',
                'DESTROYED'
        ]

        cleanup:
        context.close()

        where:
        lazy << [false, true]
    }

    private static Map<Integer, Set<Integer>> interceptorsPerTarget(List<String> events) {
        Map<Integer, Set<Integer>> result = new TreeMap<>()
        for (String event : events) {
            def parts = event.split(':')
            if (parts.length == 3 && parts[2].isInteger()) {
                result.computeIfAbsent(parts[2] as int, k -> new LinkedHashSet<>()).add(parts[0] as int)
            }
        }
        return result
    }
}

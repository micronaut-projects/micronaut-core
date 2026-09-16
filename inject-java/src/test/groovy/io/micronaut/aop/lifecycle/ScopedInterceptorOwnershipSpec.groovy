package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * An interceptor of a custom scope belongs to that scope, not to the bean it intercepts: the bean must not keep the
 * instance, and a bean whose destruction has begun must not gain a new one.
 */
class ScopedInterceptorOwnershipSpec extends AbstractTypeElementSpec {

    void 'test a scoped interceptor of a singleton target is resolved again once its scope has ended'() {
        given:
        ApplicationContext context = buildContext('''
package scoped.interceptor;

import io.micronaut.aop.*;
import io.micronaut.context.scope.AbstractConcurrentCustomScope;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.inject.BeanIdentifier;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around(proxyTarget = true)
@interface Counted {
}

// of a scope of its own: the target may not keep the instance, the scope decides when it is replaced
@Conversation
@InterceptorBean(Counted.class)
class CountingInterceptor implements MethodInterceptor<Object, Object> {
    static int instances;
    final int id = ++instances;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return id;
    }
}

@Singleton
@Counted
class TargetBean {
    public int call() { return 0; }
}
''')
        def interceptorType = context.classLoader.loadClass('scoped.interceptor.CountingInterceptor')
        def scopeType = context.classLoader.loadClass('scoped.interceptor.ConversationScope')
        def scope = context.getBean(scopeType)
        def bean = context.getBean(context.classLoader.loadClass('scoped.interceptor.TargetBean'))

        when: 'the first conversation calls the singleton target twice'
        def first = bean.call()
        def alsoFirst = bean.call()

        then: 'one instance serves the conversation'
        first == 1
        alsoFirst == 1

        when: 'the conversation ends and another calls the same target'
        scope.end('first')
        scopeType.current = 'second'
        def second = bean.call()

        then: 'the instance of the ended conversation is not used again'
        second == 2
        interceptorType.instances == 2

        cleanup:
        scopeType.current = 'first'
        context.close()
    }

    void 'test an interceptor created while a bean is being destroyed is destroyed too'() {
        given:
        ApplicationContext context = buildContext('''
package scoped.latedestroy;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Guarded {
}

// bound for pre destroy alone, so it is created for the bean only while the bean is being destroyed
@Prototype
@InterceptorBinding(value = Guarded.class, kind = InterceptorKind.PRE_DESTROY)
class GuardInterceptor implements MethodInterceptor<Object, Object> {
    static final List<String> events = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        events.add("PRE_DESTROY");
        return context.proceed();
    }

    @PreDestroy
    void close() { events.add("INTERCEPTOR_DESTROYED"); }
}

@Singleton
@Guarded
class MyBean {
    @PreDestroy
    void close() { GuardInterceptor.events.add("BEAN_DESTROYED"); }
}
''')
        def interceptorType = context.classLoader.loadClass('scoped.latedestroy.GuardInterceptor')
        context.getBean(context.classLoader.loadClass('scoped.latedestroy.MyBean'))

        when:
        context.stop()

        then: 'the interceptor created for the destruction intercepts it and is destroyed itself'
        interceptorType.events == ['PRE_DESTROY', 'BEAN_DESTROYED', 'INTERCEPTOR_DESTROYED']

        cleanup:
        context.close()
    }
}

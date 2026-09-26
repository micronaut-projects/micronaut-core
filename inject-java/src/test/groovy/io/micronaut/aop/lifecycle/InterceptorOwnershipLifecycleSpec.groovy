package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.HotSwappableInterceptedProxy
import io.micronaut.aop.Interceptor
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.inject.qualifiers.Qualifiers

/**
 * Where the ownership of interceptors meets the lifecycle of the bean that owns them: a bean created for the caller
 * is destroyed with what it owns, a destroyed bean owns nothing more, an interceptor's own dependencies are not the bean's
 * interceptors, a scoped interceptor is its scope's on every proxy shape, and a failed creation leaves nothing
 * behind.
 */
class InterceptorOwnershipLifecycleSpec extends AbstractTypeElementSpec {

    void 'test a bean created for the caller is destroyed with its dependents whatever the scope of its definition'() {
        given:
        ApplicationContext context = buildContext('''
package lifecycle.collected;

import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.*;

@Prototype
class Pen {
    static final List<String> events = new ArrayList<>();
    @PreDestroy void destroy() { events.add("PEN_DESTROYED"); }
}

@Prototype
class Writer {
    final Pen pen;
    Writer(Pen pen) { this.pen = pen; }
}

// created through createBean, the caller holds it whatever the scope of its definition
@Singleton
class SingleWriter {
    final Pen pen;
    SingleWriter(Pen pen) { this.pen = pen; }
}
''')
        def penType = context.classLoader.loadClass('lifecycle.collected.Pen')
        def writer = context.createBean(context.classLoader.loadClass('lifecycle.collected.Writer'))
        def single = context.createBean(context.classLoader.loadClass('lifecycle.collected.SingleWriter'))

        expect: 'both lead back to what they own'
        context.findBeanRegistration(writer).get().dependentBeans*.bean == [writer.pen]
        context.findBeanRegistration(single).get().dependentBeans*.bean == [single.pen]

        when:
        context.destroyBean(writer)
        context.destroyBean(single)

        then: 'destroying either bean destroys its pen'
        penType.events == ['PEN_DESTROYED', 'PEN_DESTROYED']

        cleanup:
        context.close()
    }

    void 'test an interceptor resolved for a destroyed bean belongs to nothing'() {
        given:
        ApplicationContext context = buildContext('''
package lifecycle.destroyed;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Watched {
}

@Prototype
@InterceptorBinding(value = Watched.class, kind = InterceptorKind.POST_CONSTRUCT)
class WatchingInterceptor implements MethodInterceptor<Object, Object> {
    static final List<String> events = new ArrayList<>();
    static int instances;
    WatchingInterceptor() { instances++; }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }

    @PreDestroy
    void close() { events.add("INTERCEPTOR_DESTROYED"); }
}

@Prototype
@Watched
class MyBean {
}
''')
        def interceptorType = context.classLoader.loadClass('lifecycle.destroyed.WatchingInterceptor')
        def beanType = context.classLoader.loadClass('lifecycle.destroyed.MyBean')
        def bean = context.createBean(beanType)
        def registration = context.findBeanRegistration(bean).get()
        def binding = Qualifiers.byInterceptorBinding(context.getBeanDefinition(beanType).annotationMetadata)

        when:
        context.destroyBean(registration)

        then: 'the interceptor the bean owned is destroyed with it'
        interceptorType.events == ['INTERCEPTOR_DESTROYED']

        when: 'something still resolves for the destroyed bean'
        def resolved = io.micronaut.context.RegisteredBeanInterceptors.getInterceptorRegistrations(registration, Interceptor.ARGUMENT, binding)

        then: 'it gets a working instance that is neither destroyed on the spot nor handed to the bean'
        resolved*.bean*.getClass() == [interceptorType]
        interceptorType.instances == 2
        interceptorType.events == ['INTERCEPTOR_DESTROYED']
        registration.dependentBeans.empty

        cleanup:
        context.close()
    }

    void 'test the dependencies of an interceptor are not the interceptors of those dependencies'() {
        given:
        ApplicationContext context = buildContext('''
package lifecycle.transitive;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Marked {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Outer {
}

@Prototype
@InterceptorBean(Marked.class)
class MarkingInterceptor implements MethodInterceptor<Object, Object> {
    static int instances;
    final int id = ++instances;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return id;
    }
}

// injects a marking interceptor and is intercepted by one: they are different instances
@Prototype
@Marked
class Helper {
    final MarkingInterceptor injected;
    Helper(MarkingInterceptor injected) { this.injected = injected; }
    public int interceptedBy() { return 0; }
}

@Prototype
@InterceptorBean(Outer.class)
class OuterInterceptor implements MethodInterceptor<Object, Object> {
    static Helper helper;
    OuterInterceptor(Helper helper) { OuterInterceptor.helper = helper; }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@Singleton
@Outer
class MyBean {
    public String call() { return "called"; }
}
''')
        def outerType = context.classLoader.loadClass('lifecycle.transitive.OuterInterceptor')
        context.getBean(context.classLoader.loadClass('lifecycle.transitive.MyBean')).call()
        def helper = outerType.helper

        expect: 'the helper, created as a dependency of an interceptor, is intercepted by an instance of its own'
        helper.interceptedBy() != helper.injected.id

        cleanup:
        context.close()
    }

    void 'test a runtime proxy creator that reads the methods own interceptors still applies the non-singletons'() {
        given:
        def context = buildContext('''
package lifecycle.legacycreator;

import io.micronaut.aop.*;
import io.micronaut.aop.runtime.RuntimeProxy;
import io.micronaut.context.annotation.Prototype;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Around(proxyTarget = true)
@interface Counted {
}

@Prototype
@InterceptorBean(Counted.class)
class CountingInterceptor implements MethodInterceptor<Object, Object> {
    static int calls;
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        calls++;
        return context.proceed();
    }
}

@Prototype
@Counted
@RuntimeProxy(io.micronaut.aop.LegacyByteBuddyRuntimeProxy.class)
class Fronted {
    public String call() { return "called"; }
}
''')
        context.registerSingleton(new io.micronaut.aop.LegacyByteBuddyRuntimeProxy())
        def counting = context.classLoader.loadClass('lifecycle.legacycreator.CountingInterceptor')

        when:
        def result = context.getBean(context.classLoader.loadClass('lifecycle.legacycreator.Fronted')).call()

        then: 'the prototype advice runs, as it did before a proxy could ask per target'
        result == 'called'
        counting.calls == 1

        cleanup:
        context.close()
    }

    void 'test a scoped interceptor of a runtime proxy fronting a singleton is resolved again once its scope has ended'() {
        given:
        ApplicationContext context = buildContext(conversationSource('lifecycle.runtimescoped', '@Around(proxyTarget = true)', '''
@Singleton
@Counted
@io.micronaut.aop.runtime.RuntimeProxy(io.micronaut.aop.ByteBuddyRuntimeProxy.class)
class TargetBean {
    public int call() { return 0; }
}
'''))
        context.registerSingleton(new io.micronaut.aop.ByteBuddyRuntimeProxy())
        def interceptorType = context.classLoader.loadClass('lifecycle.runtimescoped.CountingInterceptor')
        def scopeType = context.classLoader.loadClass('lifecycle.runtimescoped.ConversationScope')
        def scope = context.getBean(scopeType)
        def bean = context.getBean(context.classLoader.loadClass('lifecycle.runtimescoped.TargetBean'))

        when:
        def first = [bean.call(), bean.call()]
        scope.end('first')
        scopeType.current = 'second'
        def second = bean.call()

        then:
        first == [1, 1]
        second == 2
        interceptorType.instances == 2

        cleanup:
        scopeType.current = 'first'
        context.close()
    }

    void 'test a scoped interceptor of a target the context did not create is resolved again once its scope has ended'() {
        given:
        ApplicationContext context = buildContext(conversationSource('lifecycle.unownedscoped', '@Around(proxyTarget = true, hotswap = true)', '''
@Singleton
@Counted
class TargetBean {
    public int call() { return 0; }
}
'''))
        def interceptorType = context.classLoader.loadClass('lifecycle.unownedscoped.CountingInterceptor')
        def scopeType = context.classLoader.loadClass('lifecycle.unownedscoped.ConversationScope')
        def targetType = context.classLoader.loadClass('lifecycle.unownedscoped.TargetBean')
        def scope = context.getBean(scopeType)
        def bean = context.getBean(targetType)
        def constructor = targetType.getDeclaredConstructor()
        constructor.accessible = true
        ((HotSwappableInterceptedProxy) bean).swap(constructor.newInstance())

        when:
        def first = [bean.call(), bean.call()]
        scope.end('first')
        scopeType.current = 'second'
        def second = bean.call()

        then:
        first == [1, 1]
        second == 2
        interceptorType.instances == 2

        cleanup:
        scopeType.current = 'first'
        context.close()
    }

    void 'test a minimal custom scope populated by an ordinary lookup still hands back the registration it created'() {
        given:
        ApplicationContext context = buildContext('''
package lifecycle.minimal;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.runtime.context.scope.ScopedProxy;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@ScopedProxy
@Scope
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface Minimal {
}

// implements only what CustomScope requires: it cannot find a registration by bean or by definition
@Singleton
class MinimalScope implements CustomScope<Minimal> {
    private final Map<BeanIdentifier, CreatedBean<?>> beans = new HashMap<>();
    public Class<Minimal> annotationType() { return Minimal.class; }
    public <T> T getOrCreate(BeanCreationContext<T> creationContext) {
        return (T) beans.computeIfAbsent(creationContext.id(), id -> creationContext.create()).bean();
    }
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        CreatedBean<?> removed = beans.remove(identifier);
        return removed == null ? Optional.empty() : Optional.of((T) removed.bean());
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Counted {
}

@Prototype
@InterceptorBean(Counted.class)
class CountingInterceptor implements MethodInterceptor<Object, Object> {
    static int instances;
    final int id = ++instances;
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return id;
    }
}

@Minimal
@Counted
class MinimalBean {
    public int call() { return 0; }
}
''')
        def interceptorType = context.classLoader.loadClass('lifecycle.minimal.CountingInterceptor')
        def beanType = context.classLoader.loadClass('lifecycle.minimal.MinimalBean')

        when: 'the scope is populated by an ordinary lookup of the target before the proxy is called'
        context.getProxyTargetBean(beanType, null)
        def bean = context.getBean(beanType)
        def calls = [bean.call(), bean.call(), bean.call()]

        then: 'every call reaches the one instance the target owns'
        calls == [1, 1, 1]
        interceptorType.instances == 1

        cleanup:
        context.close()
    }

    void 'test an interceptor of a scope nothing implements is owned by the bean it intercepts'() {
        given:
        ApplicationContext context = buildContext('''
package lifecycle.noscope;

import io.micronaut.aop.*;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

// no implementation of the scope is registered, so a bean of it is created for whoever asks
@Scope
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface Nowhere {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around(proxyTarget = true)
@interface Counted {
}

@Nowhere
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
        def interceptorType = context.classLoader.loadClass('lifecycle.noscope.CountingInterceptor')
        def bean = context.getBean(context.classLoader.loadClass('lifecycle.noscope.TargetBean'))

        when:
        def calls = [bean.call(), bean.call(), bean.call()]

        then: 'one instance serves the target, as for an interceptor with no scope'
        calls == [1, 1, 1]
        interceptorType.instances == 1

        cleanup:
        context.close()
    }

    void 'test what was created for a bean that fails to be created is destroyed'() {
        given:
        ApplicationContext context = buildContext('''
package lifecycle.failed;

import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import java.util.*;

@Prototype
class Pen {
    static final List<String> events = new ArrayList<>();
    @PreDestroy void destroy() { events.add("PEN_DESTROYED"); }
}

@Prototype
class Broken {
    Broken(Pen pen) { throw new IllegalStateException("broken"); }
}
''')
        def penType = context.classLoader.loadClass('lifecycle.failed.Pen')

        when:
        context.getBean(context.classLoader.loadClass('lifecycle.failed.Broken'))

        then:
        thrown(BeanInstantiationException)
        penType.events == ['PEN_DESTROYED']

        cleanup:
        context.close()
    }

    private static String conversationSource(String pkg, String around, String target) {
        """
package ${pkg};

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
${around}
@interface Counted {
}

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
${target}
"""
    }
}

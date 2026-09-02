package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.inject.BeanDefinition

/**
 * The states a per-callback lifecycle interception can be in, beyond the shape of the callback itself, which
 * {@link LifecycleCallbackMethodSpec} covers: both kinds on one bean, a binding for one kind only, a phase with
 * no callback, the pre-destroy order, a proxied target, an interceptor that replaces the bean, skips a callback or
 * observes a failure, a per-target interceptor spanning several callbacks, a factory-declared pre-destroy method,
 * mutation of the resolved arguments and a callback with a return value.
 */
class LifecycleCallbackStatesSpec extends AbstractTypeElementSpec {

    void 'test a bean intercepting both kinds is intercepted per callback of each kind'() {
        given:
        ApplicationContext context = buildContext('''
package states.bothkinds;

import io.micronaut.aop.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static final List<String> EVENTS = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        EVENTS.add(ctx.getKind() + ":" + ctx.getExecutableMethod().getMethodName());
        return ctx.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    static final List<String> EVENTS = new ArrayList<>();

    @PostConstruct
    void init() {
        EVENTS.add("init");
    }

    @PreDestroy
    void close() {
        EVENTS.add("close");
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('states.bothkinds.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('states.bothkinds.MyBean')

        when:
        context.getBean(beanType)
        BeanDefinition<?> definition = getBeanDefinition(context, beanType.name)

        then:
        interceptorType.EVENTS == ['POST_CONSTRUCT:init']
        beanType.EVENTS == ['init']
        definition.postConstructExecutableMethods*.methodName == ['init']
        definition.preDestroyExecutableMethods*.methodName == ['close']
        definition.executableMethods.empty

        when:
        context.stop()

        then:
        interceptorType.EVENTS == ['POST_CONSTRUCT:init', 'PRE_DESTROY:close']
        beanType.EVENTS == ['init', 'close']

        cleanup:
        context.close()
    }

    void 'test a bean bound for one kind only compiles the callbacks of that kind'() {
        given:
        ApplicationContext context = buildContext('''
package states.onekind;

import io.micronaut.aop.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static final List<String> EVENTS = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        EVENTS.add(ctx.getKind() + ":" + ctx.getExecutableMethod().getMethodName());
        return ctx.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    int inits;
    int closes;

    @PostConstruct
    void init() {
        inits++;
    }

    @PreDestroy
    void close() {
        closes++;
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('states.onekind.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('states.onekind.MyBean')

        when:
        def bean = context.getBean(beanType)
        BeanDefinition<?> definition = getBeanDefinition(context, beanType.name)

        then: 'post construct ran directly, without interception, and was not compiled as an executable method'
        bean.inits == 1
        interceptorType.EVENTS.empty
        definition.postConstructExecutableMethods.empty
        definition.preDestroyExecutableMethods*.methodName == ['close']
        definition.postConstructMethods*.name == ['init']

        when:
        context.stop()

        then:
        bean.closes == 1
        interceptorType.EVENTS == ['PRE_DESTROY:close']

        cleanup:
        context.close()
    }

    void 'test pre destroy without a callback is intercepted once as a phase and callbacks in invocation order'() {
        given:
        ApplicationContext context = buildContext('''
package states.predestroy;

import io.micronaut.aop.*;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static final List<String> EVENTS = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        EVENTS.add("intercept " + ctx.getTarget().getClass().getSimpleName() + "." + ctx.getExecutableMethod().getMethodName());
        return ctx.proceed();
    }
}

@Singleton
@Tracked
class NoCallback {
}

class Base {
    @PreDestroy
    void baseClose() {
        TrackingInterceptor.EVENTS.add("baseClose");
    }
}

@Singleton
@Tracked
class Sub extends Base {
    @PreDestroy
    void subClose() {
        TrackingInterceptor.EVENTS.add("subClose");
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('states.predestroy.TrackingInterceptor')
        Class<?> noCallbackType = context.classLoader.loadClass('states.predestroy.NoCallback')
        Class<?> subType = context.classLoader.loadClass('states.predestroy.Sub')

        when:
        context.getBean(noCallbackType)
        context.getBean(subType)
        BeanDefinition<?> subDefinition = getBeanDefinition(context, subType.name)
        List<String> invocationOrder = subDefinition.preDestroyMethods*.name

        then:
        interceptorType.EVENTS.empty
        subDefinition.preDestroyExecutableMethods*.methodName == invocationOrder

        when:
        context.stop()

        then: 'the bean without a callback is intercepted once as the dispose phase'
        interceptorType.EVENTS.count('intercept NoCallback.dispose') == 1

        and: 'each callback of the hierarchy is intercepted right before it runs, in the order the definition invokes them'
        interceptorType.EVENTS.findAll { it != 'intercept NoCallback.dispose' } == invocationOrder.collectMany { ["intercept Sub.$it".toString(), it] }

        cleanup:
        context.close()
    }

    void 'test a proxy target bean exposes the callback to lifecycle advice and hides it from the proxy definition'() {
        given:
        ApplicationContext context = buildContext('''
package states.proxytarget;

import io.micronaut.aop.*;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around(proxyTarget = true)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static final List<String> EVENTS = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        EVENTS.add(ctx.getKind() + ":" + ctx.getExecutableMethod().getMethodName());
        return ctx.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    int inits;

    @PostConstruct
    void init() {
        inits++;
    }

    public String work() {
        return "done";
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('states.proxytarget.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('states.proxytarget.MyBean')

        when:
        def bean = context.getBean(beanType)
        def result = bean.work()
        def definitions = context.getBeanDefinitions(beanType)

        then:
        result == 'done'
        bean instanceof io.micronaut.aop.Intercepted
        interceptorType.EVENTS == ['POST_CONSTRUCT:init', 'AROUND:work']

        and: 'no definition of the type, proxy or target, lists the callback as an executable method'
        !definitions.empty
        definitions.every { !it.executableMethods*.methodName.contains('init') }

        cleanup:
        context.close()
    }

    void 'test the bean returned by an interceptor replaces the instance for the following callbacks and the context'() {
        given:
        ApplicationContext context = buildContext('''
package states.replace;

import io.micronaut.aop.*;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static Object original;
    static Object replacement;
    static Object secondTarget;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        if (ctx.getExecutableMethod().getMethodName().equals("first")) {
            original = ctx.getTarget();
            ctx.proceed();
            replacement = new MyBean();
            ((MyBean) replacement).replaced = true;
            return replacement;
        }
        secondTarget = ctx.getTarget();
        return ctx.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    boolean replaced;
    final List<String> invoked = new ArrayList<>();

    @PostConstruct
    void first() {
        invoked.add("first");
    }

    @PostConstruct
    void second() {
        invoked.add("second");
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('states.replace.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('states.replace.MyBean')

        when:
        def bean = context.getBean(beanType)

        then: 'the second callback is invoked on the replacement, which is what the context holds'
        bean.is(interceptorType.replacement)
        bean.replaced
        interceptorType.secondTarget.is(interceptorType.replacement)
        interceptorType.original.invoked == ['first']
        bean.invoked == ['second']

        cleanup:
        context.close()
    }

    void 'test an interceptor that does not proceed skips only its callback'() {
        given:
        ApplicationContext context = buildContext('''
package states.skip;

import io.micronaut.aop.*;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        if (ctx.getExecutableMethod().getMethodName().equals("skipped")) {
            return ctx.getTarget();
        }
        return ctx.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    final List<String> invoked = new ArrayList<>();

    @PostConstruct
    void skipped() {
        invoked.add("skipped");
    }

    @PostConstruct
    void invoked() {
        invoked.add("invoked");
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('states.skip.MyBean')

        when:
        def bean = context.getBean(beanType)

        then:
        bean.invoked == ['invoked']

        cleanup:
        context.close()
    }

    void 'test a failing callback is reported to its interceptor as thrown and fails the bean'() {
        given:
        ApplicationContext context = buildContext('''
package states.failure;

import io.micronaut.aop.*;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static final Map<String, Throwable> FAILURES = new LinkedHashMap<>();
    static final List<String> intercepted = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        intercepted.add(ctx.getExecutableMethod().getMethodName());
        try {
            return ctx.proceed();
        } catch (RuntimeException e) {
            FAILURES.put(ctx.getExecutableMethod().getMethodName(), e);
            throw e;
        }
    }
}

@Singleton
@Tracked
class PrivateFailure {
    @PostConstruct
    private void privateFailing() {
        throw new IllegalStateException("private boom");
    }

    @PostConstruct
    void afterFailure() {
    }
}

@Singleton
@Tracked
class DirectFailure {
    @PostConstruct
    void directFailing() {
        throw new IllegalStateException("direct boom");
    }

    @PostConstruct
    void afterFailure() {
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('states.failure.TrackingInterceptor')
        Class<?> privateType = context.classLoader.loadClass('states.failure.PrivateFailure')
        Class<?> directType = context.classLoader.loadClass('states.failure.DirectFailure')

        when:
        context.getBean(privateType)

        then: 'a reflectively dispatched callback reports what it threw, not the reflection wrapper'
        BeanInstantiationException privateFailure = thrown()
        interceptorType.FAILURES.privateFailing instanceof IllegalStateException
        interceptorType.FAILURES.privateFailing.message == 'private boom'
        causes(privateFailure).any { it instanceof IllegalStateException && it.message == 'private boom' }

        when:
        context.getBean(directType)

        then: 'a directly dispatched callback reports what it threw'
        BeanInstantiationException directFailure = thrown()
        interceptorType.FAILURES.directFailing instanceof IllegalStateException
        interceptorType.FAILURES.directFailing.message == 'direct boom'
        causes(directFailure).any { it instanceof IllegalStateException && it.message == 'direct boom' }

        and: 'the callback after the failing one was never intercepted'
        interceptorType.intercepted == ['privateFailing', 'directFailing']

        cleanup:
        context.close()
    }

    void 'test a per target interceptor instance serves every callback of the bean'() {
        given:
        ApplicationContext context = buildContext('''
package states.reuse;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {
}

@Prototype
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class TrackingInterceptor implements Interceptor<Object, Object> {
    static int instances;
    static final List<String> EVENTS = new ArrayList<>();

    private final int id = ++instances;

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        String event = context instanceof ConstructorInvocationContext
            ? "AROUND_CONSTRUCT"
            : ((MethodInvocationContext<?, ?>) context).getKind() + ":" + ((MethodInvocationContext<?, ?>) context).getExecutableMethod().getMethodName();
        EVENTS.add(id + ":" + event);
        return context.proceed();
    }
}

class Base {
    @PostConstruct
    void baseInit() {
    }

    @PreDestroy
    void baseClose() {
    }
}

@Singleton
@Tracked
class MyBean extends Base {
    @PostConstruct
    void init() {
    }

    public String work() {
        return "done";
    }

    @PreDestroy
    void close() {
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('states.reuse.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('states.reuse.MyBean')

        when:
        def bean = context.getBean(beanType)
        bean.work()
        context.stop()

        then: 'one instance serves construction, every post construct callback, the method call and every pre destroy callback'
        interceptorType.instances == 1
        interceptorType.EVENTS == [
                '1:AROUND_CONSTRUCT',
                '1:POST_CONSTRUCT:baseInit',
                '1:POST_CONSTRUCT:init',
                '1:AROUND:work',
                '1:PRE_DESTROY:baseClose',
                '1:PRE_DESTROY:close'
        ]

        cleanup:
        context.close()
    }

    void 'test the pre destroy method declared by a factory is the intercepted method'() {
        given:
        ApplicationContext context = buildContext('''
package states.factory;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static String methodName;
    static Class<?> declaringType;
    static Object target;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        methodName = ctx.getExecutableMethod().getMethodName();
        declaringType = ctx.getExecutableMethod().getDeclaringType();
        target = ctx.getTarget();
        return ctx.proceed();
    }
}

class Resource {
    boolean closed;

    public void close() {
        closed = true;
    }
}

@Factory
class ResourceFactory {
    @Bean(preDestroy = "close")
    @Singleton
    @Tracked
    Resource resource() {
        return new Resource();
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('states.factory.TrackingInterceptor')
        Class<?> resourceType = context.classLoader.loadClass('states.factory.Resource')

        when:
        def resource = context.getBean(resourceType)
        BeanDefinition<?> definition = getBeanDefinition(context, resourceType.name)

        then:
        !resource.closed
        definition.preDestroyExecutableMethods*.methodName == ['close']
        definition.executableMethods.empty

        when:
        context.stop()

        then:
        resource.closed
        interceptorType.methodName == 'close'
        interceptorType.declaringType == resourceType
        interceptorType.target.is(resource)

        cleanup:
        context.close()
    }

    void 'test an interceptor can replace the resolved argument of a callback'() {
        given:
        ApplicationContext context = buildContext('''
package states.mutate;

import io.micronaut.aop.*;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

class Dependency {
    final String name;

    Dependency(String name) {
        this.name = name;
    }
}

@io.micronaut.context.annotation.Factory
class DependencyProvider {
    @Singleton
    Dependency injected() {
        return new Dependency("injected");
    }
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static final Dependency REPLACEMENT = new Dependency("replacement");
    static String seen;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        seen = ((Dependency) ctx.getParameterValues()[0]).name;
        ctx.getParameterValues()[0] = REPLACEMENT;
        return ctx.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    Dependency received;

    @PostConstruct
    void init(Dependency dependency) {
        received = dependency;
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('states.mutate.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('states.mutate.MyBean')

        when:
        def bean = context.getBean(beanType)

        then:
        interceptorType.seen == 'injected'
        bean.received.is(interceptorType.REPLACEMENT)

        cleanup:
        context.close()
    }

    void 'test proceed returns the bean even when the callback returns a value'() {
        given:
        ApplicationContext context = buildContext('''
package states.returning;

import io.micronaut.aop.*;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static Object proceeded;
    static Class<?> returnType;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        returnType = ctx.getReturnType().getType();
        proceeded = ctx.proceed();
        return proceeded;
    }
}

@Singleton
@Tracked
class MyBean {
    int inits;

    @PostConstruct
    String init() {
        inits++;
        return "ignored";
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('states.returning.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('states.returning.MyBean')

        when:
        def bean = context.getBean(beanType)

        then:
        bean.inits == 1
        interceptorType.proceeded.is(bean)
        interceptorType.returnType == beanType

        cleanup:
        context.close()
    }

    private static List<Throwable> causes(Throwable t) {
        List<Throwable> result = []
        while (t != null && !result.contains(t)) {
            result << t
            t = t.cause
        }
        result
    }
}

package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.inject.BeanDefinition

/**
 * The states a lifecycle interception can be in, beyond the shape of the event itself, which
 * {@link LifecycleCallbackMethodSpec} covers: both kinds on one bean, a binding for one kind only, an event with
 * no callback, the pre-destroy order, a proxied target, an interceptor that replaces the bean, one that does not
 * proceed or observes a failure, a per-target interceptor spanning both events, a factory-declared pre-destroy
 * method, the resolved arguments of a callback and a callback with a return value.
 */
class LifecycleCallbackStatesSpec extends AbstractTypeElementSpec {

    void 'test a bean intercepting both kinds is intercepted once per event'() {
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
        interceptorType.EVENTS == ['POST_CONSTRUCT:initialize']
        beanType.EVENTS == ['init']
        definition.postConstructExecutableMethods*.methodName == ['init']
        definition.preDestroyExecutableMethods*.methodName == ['close']
        definition.executableMethods.empty

        when:
        context.stop()

        then:
        interceptorType.EVENTS == ['POST_CONSTRUCT:initialize', 'PRE_DESTROY:dispose']
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
        interceptorType.EVENTS == ['PRE_DESTROY:dispose']

        cleanup:
        context.close()
    }

    void 'test pre destroy is intercepted once as an event, with or without callbacks, which run in invocation order'() {
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

        then: 'the bean without a callback is intercepted once as the dispose event'
        interceptorType.EVENTS.count('intercept NoCallback.dispose') == 1

        and: 'the hierarchy is intercepted once, then every callback runs in the order the definition invokes them'
        invocationOrder == ['baseClose', 'subClose']
        interceptorType.EVENTS.findAll { it != 'intercept NoCallback.dispose' } == ['intercept Sub.dispose'] + invocationOrder

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
        interceptorType.EVENTS == ['POST_CONSTRUCT:initialize', 'AROUND:work']

        and: 'no definition of the type, proxy or target, lists the callback as an executable method'
        !definitions.empty
        definitions.every { !it.executableMethods*.methodName.contains('init') }

        cleanup:
        context.close()
    }

    void 'test the bean returned by an interceptor replaces the instance held by the context after every callback ran'() {
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
    static Object proceeded;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        original = ctx.getTarget();
        proceeded = ctx.proceed();
        replacement = new MyBean();
        ((MyBean) replacement).replaced = true;
        return replacement;
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

        then: 'proceed() ran every callback on the original, and the context holds the replacement'
        bean.is(interceptorType.replacement)
        bean.replaced
        interceptorType.proceeded.is(interceptorType.original)
        interceptorType.original.invoked == ['first', 'second']
        bean.invoked == []

        cleanup:
        context.close()
    }

    void 'test an interceptor that does not proceed keeps every callback of the event from running'() {
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
    static int intercepted;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        intercepted++;
        return ctx.getTarget();
    }
}

@Singleton
@Tracked
class MyBean {
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
        Class<?> interceptorType = context.classLoader.loadClass('states.skip.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('states.skip.MyBean')

        when:
        def bean = context.getBean(beanType)

        then: 'the one chain of the event was not proceeded, so no callback ran'
        interceptorType.intercepted == 1
        bean.invoked == []

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
        String target = ctx.getTarget().getClass().getSimpleName();
        intercepted.add(target + "." + ctx.getExecutableMethod().getMethodName());
        try {
            return ctx.proceed();
        } catch (RuntimeException e) {
            FAILURES.put(target, e);
            throw e;
        }
    }
}

@Singleton
@Tracked
class PrivateFailure {
    static boolean afterFailureRan;

    @PostConstruct
    private void privateFailing() {
        throw new IllegalStateException("private boom");
    }

    @PostConstruct
    void afterFailure() {
        afterFailureRan = true;
    }
}

@Singleton
@Tracked
class DirectFailure {
    static boolean afterFailureRan;

    @PostConstruct
    void directFailing() {
        throw new IllegalStateException("direct boom");
    }

    @PostConstruct
    void afterFailure() {
        afterFailureRan = true;
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
        interceptorType.FAILURES.PrivateFailure instanceof IllegalStateException
        interceptorType.FAILURES.PrivateFailure.message == 'private boom'
        causes(privateFailure).any { it instanceof IllegalStateException && it.message == 'private boom' }
        !privateType.afterFailureRan

        when:
        context.getBean(directType)

        then: 'a directly dispatched callback reports what it threw'
        BeanInstantiationException directFailure = thrown()
        interceptorType.FAILURES.DirectFailure instanceof IllegalStateException
        interceptorType.FAILURES.DirectFailure.message == 'direct boom'
        causes(directFailure).any { it instanceof IllegalStateException && it.message == 'direct boom' }
        !directType.afterFailureRan

        and: 'each event was intercepted once'
        interceptorType.intercepted == ['PrivateFailure.initialize', 'DirectFailure.initialize']

        cleanup:
        context.close()
    }

    void 'test a per target interceptor instance serves both lifecycle events of the bean'() {
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

        then: 'one instance serves construction, the post construct event, the method call and the pre destroy event'
        interceptorType.instances == 1
        interceptorType.EVENTS == [
                '1:AROUND_CONSTRUCT',
                '1:POST_CONSTRUCT:initialize',
                '1:AROUND:work',
                '1:PRE_DESTROY:dispose'
        ]

        cleanup:
        context.close()
    }

    void 'test the pre destroy method declared by a factory is exposed and invoked by the event'() {
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
        interceptorType.methodName == 'dispose'
        interceptorType.declaringType == resourceType
        interceptorType.target.is(resource)
        definition.preDestroyExecutableMethods[0].declaringType == resourceType

        cleanup:
        context.close()
    }

    void 'test the resolved arguments of a callback are not the parameter values of the event'() {
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
    static int parameterCount = -1;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        parameterCount = ctx.getParameterValues().length;
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

        then: 'the event carries no parameters; the argument is resolved for the callback when proceed() reaches it'
        interceptorType.parameterCount == 0
        bean.received.name == 'injected'

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

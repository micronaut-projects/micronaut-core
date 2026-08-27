package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class LifecycleInterceptorReuseSpec extends AbstractTypeElementSpec {

    void 'test a prototype interceptor is reused for every interception point of one bean'() {
        given:
        ApplicationContext context = buildContext('''
package reuse.proxy;

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
    static final List<String> events = new ArrayList<>();

    private final int id = ++instances;

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        String kind = context instanceof ConstructorInvocationContext
            ? "AROUND_CONSTRUCT"
            : ((MethodInvocationContext<?, ?>) context).getKind().name();
        events.add(id + ":" + kind);
        return context.proceed();
    }

    @PreDestroy
    void destroy() {
        events.add(id + ":DESTROYED");
    }
}

@Singleton
@Tracked
class MyBean {
    @PostConstruct void init() {}
    String work() { return "done"; }
    @PreDestroy void close() {}
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('reuse.proxy.TrackingInterceptor')

        when:
        def bean = context.getBean(context.classLoader.loadClass('reuse.proxy.MyBean'))
        bean.work()
        bean.work()

        then: 'one instance serves construction, post construct and every method call'
        interceptorType.instances == 1
        interceptorType.events == [
                '1:AROUND_CONSTRUCT',
                '1:POST_CONSTRUCT',
                '1:AROUND',
                '1:AROUND'
        ]

        when:
        context.stop()

        then: 'the same instance serves pre destroy and is then destroyed as a dependent'
        interceptorType.instances == 1
        interceptorType.events[-2..-1] == ['1:PRE_DESTROY', '1:DESTROYED']

        cleanup:
        context.close()
    }

    void 'test each intercepted bean gets its own interceptor instance'() {
        given:
        ApplicationContext context = buildContext('''
package reuse.pertarget;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Prototype
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements Interceptor<Object, Object> {
    static int instances;
    static final Map<Integer, Set<String>> seen = new LinkedHashMap<>();

    private final int id = ++instances;

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        seen.computeIfAbsent(id, k -> new LinkedHashSet<>())
            .add(((MethodInvocationContext<?, ?>) context).getExecutableMethod().getDeclaringType().getSimpleName());
        return context.proceed();
    }
}

@Singleton
@Tracked
class BeanA {
    @PostConstruct void init() {}
    String work() { return "a"; }
}

@Singleton
@Tracked
class BeanB {
    @PostConstruct void init() {}
    String work() { return "b"; }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('reuse.pertarget.TrackingInterceptor')

        when:
        context.getBean(context.classLoader.loadClass('reuse.pertarget.BeanA')).work()
        context.getBean(context.classLoader.loadClass('reuse.pertarget.BeanB')).work()

        then: 'two instances, and neither is shared between the two targets'
        interceptorType.instances == 2
        interceptorType.seen.keySet() as List == [1, 2]
        interceptorType.seen[1].every { it.contains('BeanA') }
        interceptorType.seen[2].every { it.contains('BeanB') }

        cleanup:
        context.close()
    }

    void 'test lifecycle advice bound by a different annotation than the around advice still applies'() {
        given:
        ApplicationContext context = buildContext('''
package reuse.mixed;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Aro {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Life {
}

@Prototype
@InterceptorBinding(value = Aro.class, kind = InterceptorKind.AROUND)
class AroundInterceptor implements Interceptor<Object, Object> {
    static int calls;
    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        calls++;
        return context.proceed();
    }
}

@Prototype
@InterceptorBinding(value = Life.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Life.class, kind = InterceptorKind.PRE_DESTROY)
class LifecycleInterceptor implements Interceptor<Object, Object> {
    static int instances;
    static int postConstructCalls;
    static int preDestroyCalls;
    static int reusedForPreDestroy;

    private final int id = ++instances;

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        InterceptorKind kind = ((MethodInvocationContext<?, ?>) context).getKind();
        if (kind == InterceptorKind.POST_CONSTRUCT) {
            postConstructCalls++;
        } else {
            preDestroyCalls++;
            if (id == 1) {
                reusedForPreDestroy++;
            }
        }
        return context.proceed();
    }
}

@Singleton
@Aro
@Life
class MyBean {
    static int inits;
    @PostConstruct void init() { inits++; }
    String work() { return "done"; }
    @PreDestroy void close() {}
}
''')
        Class<?> myBean = context.classLoader.loadClass('reuse.mixed.MyBean')
        Class<?> around = context.classLoader.loadClass('reuse.mixed.AroundInterceptor')
        Class<?> lifecycle = context.classLoader.loadClass('reuse.mixed.LifecycleInterceptor')

        when:
        context.getBean(myBean).work()

        then: 'the lifecycle interceptor is not shadowed by the around binding'
        myBean.inits == 1
        around.calls == 1
        lifecycle.postConstructCalls == 1
        lifecycle.instances == 1

        when:
        context.stop()

        then: 'and the very same instance is reused for pre destroy'
        lifecycle.preDestroyCalls == 1
        lifecycle.instances == 1
        lifecycle.reusedForPreDestroy == 1

        cleanup:
        context.close()
    }

    void 'test reuse for lifecycle advice without an around proxy'() {
        given:
        ApplicationContext context = buildContext('''
package reuse.noproxy;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Managed {
}

@Prototype
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.PRE_DESTROY)
class ManagedInterceptor implements Interceptor<Object, Object> {
    static int instances;
    static final List<String> events = new ArrayList<>();

    private final int id = ++instances;

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        String kind = context instanceof ConstructorInvocationContext
            ? "AROUND_CONSTRUCT"
            : ((MethodInvocationContext<?, ?>) context).getKind().name();
        events.add(id + ":" + kind);
        return context.proceed();
    }
}

@io.micronaut.context.annotation.Prototype
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.PRE_DESTROY)
class PrototypeInterceptor implements Interceptor<Object, Object> {
    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@Singleton
@Managed
class MyBean {
    @PostConstruct void init() {}
    @PreDestroy void close() {}
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('reuse.noproxy.ManagedInterceptor')

        when:
        context.getBean(context.classLoader.loadClass('reuse.noproxy.MyBean'))
        context.stop()

        then: 'a single instance covers construction, post construct and pre destroy'
        interceptorType.instances == 1
        interceptorType.events == ['1:AROUND_CONSTRUCT', '1:POST_CONSTRUCT', '1:PRE_DESTROY']

        cleanup:
        context.close()
    }

    void 'test reuse for a dependent prototype with lifecycle advice and no around proxy'() {
        given:
        ApplicationContext context = buildContext('''
package reuse.dependent;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Managed {
}

@Prototype
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.PRE_DESTROY)
class ManagedInterceptor implements Interceptor<Object, Object> {
    static int instances;
    static final List<String> events = new ArrayList<>();
    private final int id = ++instances;

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        String kind = context instanceof ConstructorInvocationContext
            ? "AROUND_CONSTRUCT"
            : ((MethodInvocationContext<?, ?>) context).getKind().name();
        events.add(id + ":" + kind);
        return context.proceed();
    }
}

@Prototype
@Managed
class Item {
    @PostConstruct void init() {}
    @PreDestroy void close() {}
}

@Singleton
class Holder {
    final Item item;
    Holder(Item item) { this.item = item; }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('reuse.dependent.ManagedInterceptor')

        when:
        context.getBean(context.classLoader.loadClass('reuse.dependent.Holder'))
        context.stop()

        then: 'the prototype is destroyed as a dependent, so one instance covers all three phases'
        interceptorType.instances == 1
        interceptorType.events == ['1:AROUND_CONSTRUCT', '1:POST_CONSTRUCT', '1:PRE_DESTROY']

        cleanup:
        context.close()
    }

    // destroyBean(Object) cannot find a registration for a bean created with createBean, so the registrations
    // owned by that bean are not available to the dispose call and pre destroy still resolves a new interceptor.
    void 'test a prototype created through createBean reuses the interceptor up to post construct'() {
        given:
        ApplicationContext context = buildContext('''
package reuse.createbean;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@Prototype
@interface ProductBean {
}

@Prototype
@InterceptorBinding(value = ProductBean.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = ProductBean.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = ProductBean.class, kind = InterceptorKind.PRE_DESTROY)
class ProductInterceptor implements Interceptor<Object, Object> {
    static int instances;
    static final List<String> events = new ArrayList<>();
    private final int id = ++instances;

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        String kind = context instanceof ConstructorInvocationContext
            ? "AROUND_CONSTRUCT"
            : ((MethodInvocationContext<?, ?>) context).getKind().name();
        events.add(id + ":" + kind);
        return context.proceed();
    }
}

@ProductBean
class Product {
    private final String productName;
    Product(@Parameter String productName) { this.productName = productName; }
    String getProductName() { return productName; }
    @PreDestroy void disable() {}
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('reuse.createbean.ProductInterceptor')

        when:
        def product = context.createBean(context.classLoader.loadClass('reuse.createbean.Product'), 'test')
        context.destroyBean(product)

        then: 'construction and post construct share one instance, pre destroy does not'
        interceptorType.instances == 2
        interceptorType.events == ['1:AROUND_CONSTRUCT', '1:POST_CONSTRUCT', '2:PRE_DESTROY']

        cleanup:
        context.close()
    }

    void 'test an intercepted bean resolved as a nested dependency is not confused with its consumer'() {
        given:
        ApplicationContext context = buildContext('''
package reuse.nested;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Prototype
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements Interceptor<Object, Object> {
    static int instances;
    static final Map<Integer, List<String>> postConstructs = new LinkedHashMap<>();

    private final int id = ++instances;

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        MethodInvocationContext<?, ?> mic = (MethodInvocationContext<?, ?>) context;
        if (mic.getKind() == InterceptorKind.POST_CONSTRUCT) {
            postConstructs.computeIfAbsent(id, k -> new ArrayList<>())
                .add(mic.getTarget().getClass().getName());
        }
        return context.proceed();
    }
}

@Singleton
@Tracked
class Inner {
    @PostConstruct void init() {}
    String work() { return "inner"; }
}

@Singleton
@Tracked
class Outer {
    final Inner inner;
    Outer(Inner inner) { this.inner = inner; }
    @PostConstruct void init() {}
    String work() { return "outer"; }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('reuse.nested.TrackingInterceptor')

        when:
        context.getBean(context.classLoader.loadClass('reuse.nested.Outer'))

        then: 'each bean got its own interceptor, and each interceptor saw exactly one post construct'
        interceptorType.instances == 2
        interceptorType.postConstructs.size() == 2
        interceptorType.postConstructs.values().every { it.size() == 1 }
        interceptorType.postConstructs.values().collect { it[0] }.toSet().size() == 2

        cleanup:
        context.close()
    }

    void 'test a singleton lifecycle interceptor still applies alongside a prototype one with no around proxy'() {
        given:
        ApplicationContext context = buildContext('''
package reuse.noproxysingleton;

import io.micronaut.aop.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Managed {
}

@Singleton
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.PRE_DESTROY)
class SharedInterceptor implements Interceptor<Object, Object> {
    static int instances;
    static final List<String> kinds = new ArrayList<>();

    SharedInterceptor() { instances++; }

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        kinds.add(context instanceof ConstructorInvocationContext
            ? "AROUND_CONSTRUCT"
            : ((MethodInvocationContext<?, ?>) context).getKind().name());
        return context.proceed();
    }
}

@Singleton
@Managed
class MyBean {
    @PostConstruct void init() {}
    @PreDestroy void close() {}
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('reuse.noproxysingleton.SharedInterceptor')

        when:
        context.getBean(context.classLoader.loadClass('reuse.noproxysingleton.MyBean'))
        context.stop()

        then: 'a singleton interceptor is not a dependent of the bean, so pre destroy must still find it alongside the prototype one'
        interceptorType.kinds == ['AROUND_CONSTRUCT', 'POST_CONSTRUCT', 'PRE_DESTROY']

        cleanup:
        context.close()
    }

    void 'test the registrations a proxy exposes are exactly the ones injected into it'() {
        given:
        ApplicationContext context = buildContext('''
package reuse.identity;

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
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {
}

class Seen {
    static final Map<String, Object> BY_KIND = new LinkedHashMap<>();
}

@Prototype
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class PrototypeInterceptor implements Interceptor<Object, Object> {
    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        Seen.BY_KIND.put("prototype@" + ((MethodInvocationContext<?, ?>) context).getKind().name(), this);
        return context.proceed();
    }
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class SingletonInterceptor implements Interceptor<Object, Object> {
    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        Seen.BY_KIND.put("singleton@" + ((MethodInvocationContext<?, ?>) context).getKind().name(), this);
        return context.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    @PostConstruct void init() {}
    String work() { return "done"; }
    @PreDestroy void close() {}
}
''')
        Class<?> seen = context.classLoader.loadClass('reuse.identity.Seen')
        Class<?> prototypeType = context.classLoader.loadClass('reuse.identity.PrototypeInterceptor')
        Class<?> singletonType = context.classLoader.loadClass('reuse.identity.SingletonInterceptor')

        when:
        def bean = context.getBean(context.classLoader.loadClass('reuse.identity.MyBean'))
        bean.work()
        def accessor = io.micronaut.aop.Intercepted.getMethod('$interceptorRegistrations')
        def registrations = accessor.invoke(bean)

        then: 'the proxy exposes the registrations it was constructed with, not a copy'
        bean instanceof io.micronaut.aop.Intercepted
        accessor.invoke(bean).is(registrations)
        registrations.every { it instanceof io.micronaut.context.BeanRegistration }

        and: 'every interceptor bound to the target is present exactly once'
        registrations.collect { it.beanDefinition.beanType }.toSet() == [prototypeType, singletonType].toSet()
        registrations.size() == 2

        and: 'the instances exposed are the very instances that performed the interception'
        def exposed = registrations.collectEntries { [it.beanDefinition.beanType, it.bean] }
        seen.BY_KIND['prototype@AROUND'].is(exposed[prototypeType])
        seen.BY_KIND['prototype@POST_CONSTRUCT'].is(exposed[prototypeType])
        seen.BY_KIND['singleton@POST_CONSTRUCT'].is(exposed[singletonType])

        when: 'the bean is destroyed'
        context.stop()

        then: 'pre destroy used that same instance too'
        seen.BY_KIND['prototype@PRE_DESTROY'].is(exposed[prototypeType])

        cleanup:
        context.close()
    }

    void 'test singleton interceptors are unaffected'() {
        given:
        ApplicationContext context = buildContext('''
package reuse.singleton;

import io.micronaut.aop.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class TrackingInterceptor implements Interceptor<Object, Object> {
    static int instances;
    static final List<String> kinds = new ArrayList<>();

    TrackingInterceptor() { instances++; }

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        kinds.add(((MethodInvocationContext<?, ?>) context).getKind().name());
        return context.proceed();
    }
}

@Singleton
@Tracked
class BeanA {
    @PostConstruct void init() {}
    String work() { return "a"; }
    @PreDestroy void close() {}
}

@Singleton
@Tracked
class BeanB {
    @PostConstruct void init() {}
    String work() { return "b"; }
    @PreDestroy void close() {}
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('reuse.singleton.TrackingInterceptor')

        when:
        context.getBean(context.classLoader.loadClass('reuse.singleton.BeanA')).work()
        context.getBean(context.classLoader.loadClass('reuse.singleton.BeanB')).work()
        context.stop()

        then: 'one shared instance still sees every interception point of both beans'
        interceptorType.instances == 1
        interceptorType.kinds.count { it == 'POST_CONSTRUCT' } == 2
        interceptorType.kinds.count { it == 'AROUND' } == 2
        interceptorType.kinds.count { it == 'PRE_DESTROY' } == 2

        cleanup:
        context.close()
    }
}

package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * A bean proxied with {@code proxyTarget = true}, which includes every bean a {@code @Factory} method produces with
 * around advice, has two definitions: the proxy intercepts the business methods and the target is constructed,
 * initialized and destroyed. A non-singleton interceptor is still one instance per intercepted bean, so both must use
 * the same instance for one target.
 */
class ProxyTargetInterceptorSharingSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package proxytarget.sharing;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around(proxyTarget = true)
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Paired {
}

@Prototype
@InterceptorBinding(value = Paired.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Paired.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Paired.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Paired.class, kind = InterceptorKind.PRE_DESTROY)
class PairedInterceptor implements MethodInterceptor<Object, Object>, ConstructorInterceptor<Object> {
    static int instances;
    static final List<String> events = new ArrayList<>();

    private final int id = ++instances;
    private String state = "initial";

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        if (context instanceof MethodInvocationContext<Object, Object> method) {
            return intercept(method);
        }
        return intercept((ConstructorInvocationContext<Object>) context);
    }

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        events.add(id + ":AROUND_CONSTRUCT");
        state = "constructed";
        return context.proceed();
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        if (context.getKind() == InterceptorKind.POST_CONSTRUCT) {
            state = "initialized";
        }
        events.add(id + ":" + context.getKind() + ":" + state + ":" + System.identityHashCode(context.getTarget()));
        return context.proceed();
    }

    @PreDestroy
    void destroy() {
        events.add(id + ":DESTROYED");
    }
}

@Singleton
@Paired
class ProxiedSingleton {
    @PostConstruct void init() {}
    public String work() { return "done"; }
    @PreDestroy void close() { PairedInterceptor.events.add("TARGET_DESTROYED"); }
}

@Prototype
@Paired
class ProxiedPrototype {
    @PostConstruct void init() {}
    public String work() { return "done"; }
}

class Produced {
    @PostConstruct void init() {}
    public String work() { return "done"; }
    void close() { PairedInterceptor.events.add("TARGET_DESTROYED"); }
}

class ProducedPrototype {
    @PostConstruct void init() {}
    public String work() { return "done"; }
}

@Factory
class ProducingFactory {
    @Bean(preDestroy = "close")
    @Singleton
    @Paired
    Produced produced() {
        return new Produced();
    }

    @Bean
    @Prototype
    @Paired
    ProducedPrototype producedPrototype() {
        return new ProducedPrototype();
    }
}
'''

    void 'test one interceptor instance serves every phase of a #description'() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        Class<?> interceptorType = context.classLoader.loadClass('proxytarget.sharing.PairedInterceptor')
        Class<?> beanType = context.classLoader.loadClass('proxytarget.sharing.' + beanName)

        when:
        def bean = context.getBean(beanType)
        bean.work()
        bean.work()
        List<String> events = interceptorType.events.toList()
        println "$description events: $events"

        then: 'post construct and every method call run on the same instance, which sees the state post construct set'
        def phases = events.findAll { it.contains(':POST_CONSTRUCT:') || it.contains(':AROUND:') }
        phases.size() == 3
        phases.collect { it.split(':')[0] }.unique().size() == 1
        phases.collect { it.split(':')[1..2].join(':') } == ['POST_CONSTRUCT:initialized', 'AROUND:initialized', 'AROUND:initialized']
        phases.collect { it.split(':')[3] }.unique().size() == 1

        when:
        String id = phases.first().split(':')[0]
        interceptorType.events.clear()
        context.stop()
        events = interceptorType.events.toList()
        println "$description destroy events: $events"

        then: 'the same instance serves pre destroy and is destroyed once, after the target'
        events.findAll { it.contains(':PRE_DESTROY:') }.collect { it.split(':')[0] } == [id]
        events.count("$id:DESTROYED".toString()) == 1
        events.indexOf('TARGET_DESTROYED') >= 0
        events.indexOf('TARGET_DESTROYED') < events.indexOf("$id:DESTROYED".toString())

        cleanup:
        interceptorType.events.clear()
        context.close()

        where:
        description                   | beanName
        'proxyTarget singleton'       | 'ProxiedSingleton'
        'factory produced singleton'  | 'Produced'
    }

    void 'test each target of a #description gets its own interceptor instance for post construct and its methods'() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        Class<?> interceptorType = context.classLoader.loadClass('proxytarget.sharing.PairedInterceptor')
        Class<?> beanType = context.classLoader.loadClass('proxytarget.sharing.' + beanName)

        when:
        def first = context.getBean(beanType)
        def second = context.getBean(beanType)
        first.work()
        second.work()
        first.work()
        List<String> events = interceptorType.events.toList()
        println "$description events: $events"
        // target identity -> interceptor ids that intercepted it
        Map<String, Set<String>> perTarget = [:].withDefault { new LinkedHashSet<String>() }
        events.findAll { it.contains(':POST_CONSTRUCT:') || it.contains(':AROUND:') }.each {
            def parts = it.split(':')
            perTarget[parts[3]] << parts[0]
        }

        then: 'every target is intercepted by exactly one instance, and different targets by different instances'
        perTarget.size() == 2
        perTarget.values().every { it.size() == 1 }
        perTarget.values().collect { it.first() }.unique().size() == 2
        events.findAll { it.contains(':AROUND:') }.every { it.contains(':initialized:') }

        cleanup:
        interceptorType.events.clear()
        context.close()

        where:
        description                   | beanName
        'proxyTarget prototype'       | 'ProxiedPrototype'
        'factory produced prototype'  | 'ProducedPrototype'
    }

    void 'test a proxy target with post construct advice and no constructor advice shares the instance'() {
        given:
        ApplicationContext context = buildContext('''
package proxytarget.postconstruct;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around(proxyTarget = true)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Paired {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Released {
}

@Prototype
@InterceptorBinding(value = Paired.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Paired.class, kind = InterceptorKind.POST_CONSTRUCT)
class PairedInterceptor implements MethodInterceptor<Object, Object> {
    static int instances;
    static final List<String> events = new ArrayList<>();

    private final int id = ++instances;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        events.add(id + ":" + context.getKind());
        return context.proceed();
    }

    @PreDestroy
    void destroy() {
        events.add(id + ":DESTROYED");
    }
}

@Prototype
@InterceptorBinding(value = Released.class, kind = InterceptorKind.PRE_DESTROY)
class ReleasingInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        PairedInterceptor.events.add("released:" + context.getKind());
        return context.proceed();
    }
}

@Singleton
@InterceptorBinding(value = Paired.class, kind = InterceptorKind.AROUND)
class SharedInterceptor implements MethodInterceptor<Object, Object> {
    static int instances;

    SharedInterceptor() {
        instances++;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@Singleton
@Paired
@Released
class MyBean {
    @PostConstruct void init() {}
    public String work() { return "done"; }
    @PreDestroy void close() { PairedInterceptor.events.add("TARGET_DESTROYED"); }
}

@Prototype
@Paired
class OtherBean {
    @PostConstruct void init() {}
    public String work() { return "done"; }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('proxytarget.postconstruct.PairedInterceptor')
        Class<?> sharedType = context.classLoader.loadClass('proxytarget.postconstruct.SharedInterceptor')
        Class<?> beanType = context.classLoader.loadClass('proxytarget.postconstruct.MyBean')

        when:
        context.getBean(beanType).work()
        context.getBean(context.classLoader.loadClass('proxytarget.postconstruct.OtherBean')).work()

        then: 'each target has one prototype instance for post construct and its methods, the singleton is shared'
        interceptorType.instances == 2
        interceptorType.events.toList() == ['1:POST_CONSTRUCT', '1:AROUND', '2:POST_CONSTRUCT', '2:AROUND']
        sharedType.instances == 1

        when:
        interceptorType.events.clear()
        context.stop()
        def events = interceptorType.events.toList()

        then: 'pre destroy bound by another annotation still applies, and the adopted instance is destroyed once'
        events.count('released:PRE_DESTROY') == 1
        events.count('TARGET_DESTROYED') == 1
        events.count('1:DESTROYED') == 1
        events.indexOf('TARGET_DESTROYED') < events.indexOf('1:DESTROYED')

        cleanup:
        interceptorType.events.clear()
        context.close()
    }

    void 'test a lazy proxy target and a hotswap proxy still intercept every call'() {
        given:
        ApplicationContext context = buildContext('''
package proxytarget.lazy;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around(proxyTarget = true, lazy = true)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface LazyPaired {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around(proxyTarget = true, hotswap = true)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Swappable {
}

@Prototype
@InterceptorBinding(value = LazyPaired.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = LazyPaired.class, kind = InterceptorKind.POST_CONSTRUCT)
class PairedInterceptor implements MethodInterceptor<Object, Object> {
    static int instances;
    static final List<String> events = new ArrayList<>();

    private final int id = ++instances;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        events.add(context.getTarget().getClass().getSimpleName() + ":" + id + ":" + context.getKind());
        return context.proceed();
    }
}

@Prototype
@InterceptorBinding(value = Swappable.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Swappable.class, kind = InterceptorKind.POST_CONSTRUCT)
class SwappableInterceptor extends PairedInterceptor {
}

@Singleton
@LazyPaired
class LazyBean {
    @PostConstruct void init() {}
    public String work() { return "lazy"; }
}

@Prototype
@Swappable
class SwappableBean {
    @PostConstruct void init() {}
    public String work() { return "swappable"; }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('proxytarget.lazy.PairedInterceptor')
        Class<?> swappableType = context.classLoader.loadClass('proxytarget.lazy.SwappableBean')

        when:
        def lazy = context.getBean(context.classLoader.loadClass('proxytarget.lazy.LazyBean'))
        def swappable = context.getBean(swappableType)
        String lazyResult = lazy.work()
        String swappableResult = swappable.work()
        List<String> events = interceptorType.events.toList()
        println "lazy and hotswap events: $events"

        then:
        lazyResult == 'lazy'
        swappableResult == 'swappable'
        events.count { it.endsWith(':AROUND') } == 2
        events.count { it.endsWith(':POST_CONSTRUCT') } == 2

        and: 'the hotswap proxy created its target, so they share one instance'
        events.findAll { it.startsWith('SwappableBean:') }.collect { it.split(':')[1] }.unique().size() == 1

        when: 'another target is swapped in'
        def replacement = context.getBean(swappableType).interceptedTarget()
        swappable.swap(replacement)

        String swappedResult = swappable.work()
        events = interceptorType.events.toList()
        println "swapped events: $events"

        then: 'calls keep being intercepted by the instance the proxy was constructed with'
        swappedResult == 'swappable'
        events.findAll { it.startsWith('SwappableBean:') && it.endsWith(':AROUND') }.collect { it.split(':')[1] }.unique().size() == 1

        cleanup:
        interceptorType.events.clear()
        context.close()
    }

    void 'test a prototype argument of an intercepted factory method is not destroyed as the factory'() {
        given:
        ApplicationContext context = buildContext('''
package proxytarget.factoryargument;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@AroundConstruct
@interface Constructed {
}

@Singleton
@InterceptorBean(Constructed.class)
class ConstructedInterceptor implements ConstructorInterceptor<Object> {
    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        return context.proceed();
    }
}

@Prototype
class Part {
    static final List<String> events = new ArrayList<>();

    @PreDestroy
    void destroy() {
        events.add("PART_DESTROYED");
    }
}

class Product {
    final Part part;

    Product(Part part) {
        this.part = part;
    }
}

@Factory
class ProductFactory {
    @Bean
    @Singleton
    @Constructed
    Product product(Part part) {
        return new Product(part);
    }
}
''')
        Class<?> partType = context.classLoader.loadClass('proxytarget.factoryargument.Part')

        when:
        context.getBean(context.classLoader.loadClass('proxytarget.factoryargument.Product'))

        then: 'the argument lives as long as the product'
        partType.events.isEmpty()

        when:
        context.stop()

        then:
        partType.events.toList() == ['PART_DESTROYED']

        cleanup:
        partType.events.clear()
        context.close()
    }
}

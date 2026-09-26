package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.InterceptedProxy
import io.micronaut.context.ApplicationContext

/**
 * More of the scenarios micronaut-jakarta-interceptors tests, written with Micronaut interceptors alone: its advice
 * is one prototype bound for every kind, which must be one instance for each object, found once among the object's
 * dependents, whichever proxy fronts it, however it is destroyed, and whatever else is created while it is.
 */
class InterceptorInstanceScenariosSpec extends AbstractTypeElementSpec {

    private static String source(String pkg, String around, String scope) {
        """
package ${pkg};

import io.micronaut.aop.*;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.*;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
${around}
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Paired {
}

// one class for every kind, as the advice of micronaut-jakarta-interceptors is
@Prototype
@InterceptorBinding(value = Paired.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Paired.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Paired.class, kind = InterceptorKind.PRE_DESTROY)
class PairedInterceptor implements MethodInterceptor<Object, Object> {
    static final List<PairedInterceptor> CREATED = new ArrayList<>();
    static final List<PairedInterceptor> POST_CONSTRUCTED = new ArrayList<>();
    static final List<PairedInterceptor> INVOKED = new ArrayList<>();
    static final List<PairedInterceptor> PRE_DESTROYED = new ArrayList<>();
    static final List<PairedInterceptor> DESTROYED = new ArrayList<>();

    PairedInterceptor() { CREATED.add(this); }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        switch (context.getKind()) {
            case POST_CONSTRUCT -> POST_CONSTRUCTED.add(this);
            case PRE_DESTROY -> PRE_DESTROYED.add(this);
            case AROUND -> INVOKED.add(this);
            default -> { }
        }
        return context.proceed();
    }

    @PreDestroy
    void destroy() {
        DESTROYED.add(this);
    }
}

@${scope}
@Paired
class TargetService {
    public String work() { return "done"; }
}

// intercepted as well, and created by a listener of the target while the target is being created
@Prototype
@Paired
class MeddledService {
    public String work() { return "meddled"; }
}

@Singleton
class Meddler implements BeanCreatedEventListener<TargetService> {
    static boolean enabled;
    private final BeanProvider<MeddledService> meddled;
    Meddler(BeanProvider<MeddledService> meddled) { this.meddled = meddled; }
    @Override
    public TargetService onCreated(BeanCreatedEvent<TargetService> event) {
        if (enabled && !(event.getBean() instanceof InterceptedProxy)) {
            meddled.get();
        }
        return event.getBean();
    }
}
"""
    }

    void 'test a prototype interceptor bound for every kind is one instance among the dependents of a #description'() {
        given:
        ApplicationContext context = buildContext(source(pkg, around, 'Singleton'))
        def interceptor = context.classLoader.loadClass(pkg + '.PairedInterceptor')
        def bean = context.getBean(context.classLoader.loadClass(pkg + '.TargetService'))
        def target = bean instanceof InterceptedProxy ? bean.interceptedTarget() : bean
        bean.work()

        when:
        def owned = context.findBeanRegistration(target).get().dependentBeans*.bean.findAll { interceptor.isInstance(it) }

        then: 'the object owns the one instance, and every interception of it used that instance'
        owned.size() == 1
        interceptor.CREATED == owned
        interceptor.POST_CONSTRUCTED == owned
        interceptor.INVOKED == owned

        when:
        context.stop()

        then:
        interceptor.PRE_DESTROYED == owned
        interceptor.DESTROYED == owned

        cleanup:
        context.close()

        where:
        description      | pkg                   | around
        'subclass proxy' | 'instances.ident.sub' | '@Around'
        'proxy target'   | 'instances.ident.tgt' | '@Around(proxyTarget = true)'
    }

    void 'test each instance of a proxy target prototype has an interceptor of its own, destroyed with it #how'() {
        given:
        ApplicationContext context = buildContext(source(pkg, '@Around(proxyTarget = true)', 'Prototype'))
        def interceptor = context.classLoader.loadClass(pkg + '.PairedInterceptor')
        def type = context.classLoader.loadClass(pkg + '.TargetService')
        def firstRegistration = context.getBeanRegistration(type, null)
        def first = firstRegistration.bean
        def second = context.getBean(type)
        first.work()
        second.work()

        expect: 'one instance for each object, each intercepting its own'
        interceptor.CREATED.size() == 2
        interceptor.POST_CONSTRUCTED == interceptor.CREATED
        interceptor.INVOKED == interceptor.CREATED

        when:
        if (byRegistration) {
            context.destroyBean(firstRegistration)
        } else {
            context.destroyBean(first)
        }

        then: 'destroying the first destroys its interceptor alone, after intercepting its pre destroy'
        interceptor.PRE_DESTROYED == [interceptor.CREATED[0]]
        interceptor.DESTROYED == [interceptor.CREATED[0]]

        cleanup:
        context.close()

        where:
        how                    | pkg                    | byRegistration
        'by instance'          | 'instances.proto.inst' | false
        'through registration' | 'instances.proto.reg'  | true
    }

    void 'test a bean a factory method binds with an interceptor binding alone is intercepted with its own instance'() {
        given: 'a binding declared by @InterceptorBinding alone, with no @Around stereotype, as micronaut-jakarta-interceptors declares its own'
        ApplicationContext context = buildContext('''
package instances.produced;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.AROUND)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Bound {
}

@Prototype
@InterceptorBinding(value = Bound.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Bound.class, kind = InterceptorKind.POST_CONSTRUCT)
class BoundInterceptor implements MethodInterceptor<Object, Object> {
    static final List<BoundInterceptor> POST_CONSTRUCTED = new ArrayList<>();
    static final List<BoundInterceptor> INVOKED = new ArrayList<>();
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        if (context.getKind() == InterceptorKind.POST_CONSTRUCT) {
            POST_CONSTRUCTED.add(this);
        } else {
            INVOKED.add(this);
        }
        return context.proceed();
    }
}

class Product {
    public String work() { return "done"; }
}

@Factory
class ProductFactory {
    @Singleton
    @Bound
    Product product() { return new Product(); }
}
''')
        def interceptor = context.classLoader.loadClass('instances.produced.BoundInterceptor')

        when:
        def bean = context.getBean(context.classLoader.loadClass('instances.produced.Product'))

        then:
        bean.work() == 'done'

        and: 'the business method is intercepted, by the instance that saw the post construct of the product'
        interceptor.POST_CONSTRUCTED.size() == 1
        interceptor.INVOKED.size() == 1
        interceptor.INVOKED[0].is(interceptor.POST_CONSTRUCTED[0])

        cleanup:
        context.close()
    }

    void 'test a bean created while the target of a proxy is being created has an interceptor of its own'() {
        given:
        String pkg = 'instances.meddled'
        ApplicationContext context = buildContext(source(pkg, '@Around(proxyTarget = true)', 'Singleton'))
        def interceptor = context.classLoader.loadClass(pkg + '.PairedInterceptor')
        context.classLoader.loadClass(pkg + '.Meddler').enabled = true

        when: 'a listener of the target creates another intercepted bean before the proxy is complete'
        def bean = context.getBean(context.classLoader.loadClass(pkg + '.TargetService'))
        bean.work()

        then: 'the target and the other bean each have their own instance, and the proxy uses the target one'
        interceptor.CREATED.size() == 2
        interceptor.POST_CONSTRUCTED.size() == 2
        interceptor.INVOKED.size() == 1
        def targetInterceptor = context.findBeanRegistration(bean.interceptedTarget()).get().dependentBeans*.bean.find { interceptor.isInstance(it) }
        interceptor.INVOKED[0].is(targetInterceptor)

        cleanup:
        context.close()
    }
}

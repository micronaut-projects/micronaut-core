package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.InterceptedProxy
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationUtil

/**
 * A binding annotation declaring {@code @InterceptorBinding(lazyInterceptorsPerTarget = true)} opts every proxy with a
 * separate target it binds into interceptors of each target, as {@code @Around(lazyInterceptorsPerTarget = true)}
 * does: whatever {@code @Around} the bean declares or its scope contributes, and whether the annotation is declared on
 * the bean, on a method or on the factory member producing it. A binding without it leaves the proxy as it was.
 */
class BindingInterceptorsPerTargetSpec extends AbstractTypeElementSpec {

    private static String source(String pkg, boolean perTarget, String beans) {
        source(pkg, "@InterceptorBinding(kind = InterceptorKind.AROUND${perTarget ? ', lazyInterceptorsPerTarget = true' : ''})", beans)
    }

    private static String source(String pkg, String aroundBinding, String beans) {
        """
package ${pkg};

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.runtime.context.scope.ThreadLocal;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
$aroundBinding
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
    static final List<PairedInterceptor> CREATED = Collections.synchronizedList(new ArrayList<>());
    static final List<PairedInterceptor> DESTROYED = Collections.synchronizedList(new ArrayList<>());
    // target -> kind -> the instance that intercepted that kind of that target
    static final Map<Object, Map<String, Object>> SEEN = Collections.synchronizedMap(new IdentityHashMap<>());

    PairedInterceptor() { CREATED.add(this); }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        SEEN.computeIfAbsent(context.getTarget(), t -> Collections.synchronizedMap(new LinkedHashMap<>()))
            .put(context.getKind().name(), this);
        return context.proceed();
    }

    @PreDestroy
    void destroy() { DESTROYED.add(this); }
}

$beans
"""
    }

    private static String proxyTargetBean(boolean onClass) {
        """
@Singleton
@Around(proxyTarget = true)
${onClass ? '@Paired' : ''}
class TargetService {
    ${onClass ? '' : '@Paired'}
    public String work() { return "done"; }
}
"""
    }

    private static String threadLocalBean(boolean onClass) {
        """
@ThreadLocal
${onClass ? '@Paired' : ''}
class ScopedService {
    ${onClass ? '' : '@Paired'}
    public String work() { return "done"; }
}
"""
    }

    /**
     * The instances of the interceptor among the dependents of the target's registration.
     */
    private static List<Object> ownedBy(ApplicationContext context, Object target, Class<?> interceptor) {
        context.findBeanRegistration(target).get().dependentBeans()*.bean.findAll { interceptor.isInstance(it) }
    }

    /**
     * Calls the bean twice on this thread and once on another, which has a target of its own when the bean is
     * thread local.
     */
    private static void callOnTwoThreads(Object bean) {
        bean.work()
        bean.work()
        Thread other = new Thread({ bean.work() })
        other.start()
        other.join()
    }

    void 'test the binding opts an @Around(proxyTarget = true) bean bound on the #element into the interceptors of its target'() {
        given:
        ApplicationContext context = buildContext(source(pkg, true, proxyTargetBean(onClass)))
        def interceptor = context.classLoader.loadClass(pkg + '.PairedInterceptor')
        def bean = context.getBean(context.classLoader.loadClass(pkg + '.TargetService'))
        def target = ((InterceptedProxy) bean).interceptedTarget()

        when:
        bean.work()
        bean.work()
        def owned = ownedBy(context, target, interceptor)

        then: 'the target owns the one instance, which intercepted its methods and, bound on the class, its post construct'
        owned.size() == 1
        interceptor.CREATED == owned
        interceptor.SEEN.size() == 1
        interceptor.SEEN[target].keySet() == (onClass ? ['POST_CONSTRUCT', 'AROUND'] : ['AROUND']) as Set
        interceptor.SEEN[target].values().every { it.is(owned[0]) }

        when:
        context.stop()

        then: 'it is destroyed with the target'
        interceptor.DESTROYED == owned
        !onClass || interceptor.SEEN[target]['PRE_DESTROY'].is(owned[0])

        cleanup:
        context.close()

        where:
        element  | onClass | pkg
        'class'  | true    | 'bindingpertarget.fixed.cls'
        'method' | false   | 'bindingpertarget.fixed.mth'
    }

    void 'test without the member an @Around(proxyTarget = true) bean bound on the #element is intercepted by an instance of the proxy'() {
        given:
        ApplicationContext context = buildContext(source(pkg, false, proxyTargetBean(onClass)))
        def interceptor = context.classLoader.loadClass(pkg + '.PairedInterceptor')
        def bean = context.getBean(context.classLoader.loadClass(pkg + '.TargetService'))
        def target = ((InterceptedProxy) bean).interceptedTarget()

        when:
        bean.work()
        def owned = ownedBy(context, target, interceptor)
        def aroundBy = interceptor.SEEN[target]['AROUND']

        then: 'the proxy intercepts the methods with an instance the target does not own, as before'
        interceptor.CREATED.size() == (onClass ? 2 : 1)
        owned.size() == (onClass ? 1 : 0)
        !owned.any { it.is(aroundBy) }
        !onClass || interceptor.SEEN[target]['POST_CONSTRUCT'].is(owned[0])

        cleanup:
        context.close()

        where:
        element  | onClass | pkg
        'class'  | true    | 'bindingpertarget.fixednot.cls'
        'method' | false   | 'bindingpertarget.fixednot.mth'
    }

    void 'test the binding opts a thread local bean bound on the #element into the interceptors of each target'() {
        given: 'a scoped bean that declares no @Around of its own'
        ApplicationContext context = buildContext(pkg + '.ScopedService', source(pkg, true, threadLocalBean(onClass)), true)
        def interceptor = context.classLoader.loadClass(pkg + '.PairedInterceptor')
        def bean = context.getBean(context.classLoader.loadClass(pkg + '.ScopedService'))

        when:
        callOnTwoThreads(bean)
        List<Map<String, Object>> seen = interceptor.SEEN.values().toList()

        then: 'each thread had its own target, intercepted in every phase by one instance of its own'
        seen.size() == 2
        seen.every { byKind -> byKind.values().every { it.is(byKind['AROUND']) } }
        !seen[0]['AROUND'].is(seen[1]['AROUND'])
        !onClass || seen.every { it.containsKey('POST_CONSTRUCT') }

        and: 'the proxy created none'
        interceptor.CREATED.size() == 2

        cleanup:
        context.close()

        where:
        element  | onClass | pkg
        'class'  | true    | 'bindingpertarget.scoped.cls'
        'method' | false   | 'bindingpertarget.scoped.mth'
    }

    void 'test without the member a thread local bean bound on the #element is intercepted by the instance of the proxy for every target'() {
        given:
        ApplicationContext context = buildContext(pkg + '.ScopedService', source(pkg, false, threadLocalBean(onClass)), true)
        def interceptor = context.classLoader.loadClass(pkg + '.PairedInterceptor')
        def bean = context.getBean(context.classLoader.loadClass(pkg + '.ScopedService'))

        when:
        callOnTwoThreads(bean)
        List<Map<String, Object>> seen = interceptor.SEEN.values().toList()

        then: 'both targets are intercepted by the one instance of the proxy, as before'
        seen.size() == 2
        seen[0]['AROUND'].is(seen[1]['AROUND'])
        interceptor.CREATED.size() == (onClass ? 3 : 1)
        !onClass || seen.every { !it['POST_CONSTRUCT'].is(it['AROUND']) }

        cleanup:
        context.close()

        where:
        element  | onClass | pkg
        'class'  | true    | 'bindingpertarget.scopednot.cls'
        'method' | false   | 'bindingpertarget.scopednot.mth'
    }

    void 'test the binding opts a #scope bean a factory member binds into the interceptors of its target'() {
        given: 'the binding is declared on the member alone, which declares no @Around'
        ApplicationContext context = buildContext(pkg + '.ProductFactory', source(pkg, true, """
class Product {
    public String work() { return "done"; }
}

@Factory
class ProductFactory {
    @$scope
    @Paired
    Product product() { return new Product(); }
}
"""), true)
        def interceptor = context.classLoader.loadClass(pkg + '.PairedInterceptor')
        def bean = context.getBean(context.classLoader.loadClass(pkg + '.Product'))

        when:
        callOnTwoThreads(bean)
        List<Map<String, Object>> seen = interceptor.SEEN.values().toList()

        then: 'each target was intercepted in every phase by one instance of its own, which the proxy did not create'
        bean instanceof InterceptedProxy
        seen.size() == targets
        seen.every { byKind -> byKind.keySet() == ['POST_CONSTRUCT', 'AROUND'] as Set && byKind['POST_CONSTRUCT'].is(byKind['AROUND']) }
        interceptor.CREATED.size() == targets

        cleanup:
        context.close()

        where:
        scope         | targets | pkg
        'Singleton'   | 1       | 'bindingpertarget.produced.singleton'
        'ThreadLocal' | 2       | 'bindingpertarget.produced.scoped'
    }

    void 'test the member is read next to an @Around on the binding annotation, for a thread local bean bound on a method'() {
        given: 'a binding annotation declaring @Around, whose own binding carries the member and the default kind'
        String pkg = 'bindingpertarget.witharound'
        ApplicationContext context = buildContext(pkg + '.ScopedService', source(pkg, '@Around\n@InterceptorBinding(lazyInterceptorsPerTarget = true)', threadLocalBean(false)), true)
        def interceptor = context.classLoader.loadClass(pkg + '.PairedInterceptor')
        def bean = context.getBean(context.classLoader.loadClass(pkg + '.ScopedService'))

        when:
        callOnTwoThreads(bean)
        List<Map<String, Object>> seen = interceptor.SEEN.values().toList()

        then: 'each target has an instance of its own, and the proxy created none'
        seen.size() == 2
        !seen[0]['AROUND'].is(seen[1]['AROUND'])
        interceptor.CREATED.size() == 2

        cleanup:
        context.close()
    }

    void 'test a binding of the constructor opts the proxy in, for the interceptors of a binding that does not'() {
        given: 'the binding of the constructor declares the member, and the binding of the method does not'
        String pkg = 'bindingpertarget.constructor'
        ApplicationContext context = buildContext(source(pkg, false, '''
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.CONSTRUCTOR)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT, lazyInterceptorsPerTarget = true)
@interface Constructed {
}

@Singleton
@InterceptorBinding(value = Constructed.class, kind = InterceptorKind.AROUND_CONSTRUCT)
class ConstructedInterceptor implements ConstructorInterceptor<Object> {
    static final List<Object> CONSTRUCTED = new ArrayList<>();

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        Object constructed = context.proceed();
        CONSTRUCTED.add(constructed);
        return constructed;
    }
}

@Singleton
@Around(proxyTarget = true)
class TargetService {
    @Constructed
    TargetService() {
    }

    @Paired
    public String work() { return "done"; }
}
'''))
        def interceptor = context.classLoader.loadClass(pkg + '.PairedInterceptor')
        def bean = context.getBean(context.classLoader.loadClass(pkg + '.TargetService'))
        def target = ((InterceptedProxy) bean).interceptedTarget()

        when:
        bean.work()
        def owned = ownedBy(context, target, interceptor)

        then: 'the construction of the target is intercepted'
        context.classLoader.loadClass(pkg + '.ConstructedInterceptor').CONSTRUCTED.any { it.is(target) }

        and: 'the target owns the instance that intercepted its method, and the proxy created none'
        owned.size() == 1
        interceptor.CREATED == owned
        interceptor.SEEN[target]['AROUND'].is(owned[0])

        cleanup:
        context.close()
    }

    void 'test a proxy is generated as before unless a binding opts it in, lazy=#lazy perTarget=#perTarget'() {
        given:
        String name = lazy ? 'ScopedService' : 'TargetService'
        ApplicationContext context = buildContext(pkg + '.' + name, source(pkg, perTarget, lazy ? threadLocalBean(true) : proxyTargetBean(true)), true)
        Class<?> type = context.classLoader.loadClass(pkg + '.' + name)
        List<String> fields = context.getBean(type).getClass().declaredFields*.name
        def interceptorsArgument = context.getBeanDefinition(type).constructor.arguments.find { it.name == '$interceptors' }

        expect: 'a proxy that is not opted in keeps its interceptors, is not handed the registration of its target, and is injected with every interceptor'
        fields.contains('$interceptors') == !(lazy && perTarget)
        fields.contains('$targetRegistration') == (!lazy && perTarget)
        interceptorsArgument.annotationMetadata.booleanValue(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER, 'singletonsOnly').orElse(false) == perTarget

        cleanup:
        context.close()

        where:
        lazy  | perTarget | pkg
        false | false     | 'bindingpertarget.generated.fixed'
        false | true      | 'bindingpertarget.generated.fixedper'
        true  | false     | 'bindingpertarget.generated.lazy'
        true  | true      | 'bindingpertarget.generated.lazyper'
    }
}

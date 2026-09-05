package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition

/**
 * One interceptor chain runs per lifecycle event, as Jakarta Interceptors 2.2 sections 2.5 and 4.2 require:
 * {@code proceed()} in the last interceptor invokes every callback of the event, superclass first, so an
 * interceptor that does not proceed keeps every callback from running (assertion j of the compatibility kit). The
 * callbacks stay reachable through the bean definition, in invocation order, for an interceptor that needs them.
 */
class LifecycleEventChainSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package event.chain;

import io.micronaut.aop.*;
import io.micronaut.core.annotation.Order;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Guarded {
}

class Events {
    static final List<String> SEQUENCE = new ArrayList<>();
    static boolean proceed = true;
}

@Singleton @Order(10)
@InterceptorBinding(value = Guarded.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Guarded.class, kind = InterceptorKind.PRE_DESTROY)
class Gate implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        Events.SEQUENCE.add("Gate:" + ctx.getKind());
        if (!Events.proceed) {
            return ctx.getTarget();
        }
        return ctx.proceed();
    }
}

@Singleton @Order(20)
@InterceptorBinding(value = Guarded.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Guarded.class, kind = InterceptorKind.PRE_DESTROY)
class Downstream implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        Events.SEQUENCE.add("Downstream:" + ctx.getKind());
        return ctx.proceed();
    }
}

class Weapon {
    @PostConstruct void postConstructWeapon() { Events.SEQUENCE.add("Weapon"); }
    @PreDestroy void preDestroyWeapon() { Events.SEQUENCE.add("Weapon"); }
}

@Singleton
@Guarded
class Rocket extends Weapon {
    @PostConstruct void postConstruct() { Events.SEQUENCE.add("Rocket"); }
    @PreDestroy private void preDestroy() { Events.SEQUENCE.add("Rocket"); }
}
'''

    void 'test an interceptor that does not proceed keeps every callback of the event from running'() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        Class<?> events = context.classLoader.loadClass('event.chain.Events')
        Class<?> rocketType = context.classLoader.loadClass('event.chain.Rocket')
        events.proceed = false

        when:
        def bean = context.getBean(rocketType)

        then: 'neither the callbacks nor the interceptors after the gate ran, and the bean is the one the gate returned'
        bean != null
        events.SEQUENCE == ['Gate:POST_CONSTRUCT']

        when:
        events.SEQUENCE.clear()
        context.destroyBean(bean)

        then:
        events.SEQUENCE == ['Gate:PRE_DESTROY']

        cleanup:
        context.close()
    }

    void 'test proceeding the chain of the event invokes every callback, superclass first, for both events'() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        Class<?> events = context.classLoader.loadClass('event.chain.Events')
        Class<?> rocketType = context.classLoader.loadClass('event.chain.Rocket')
        events.proceed = true

        when:
        def bean = context.getBean(rocketType)

        then:
        events.SEQUENCE == ['Gate:POST_CONSTRUCT', 'Downstream:POST_CONSTRUCT', 'Weapon', 'Rocket']

        when:
        events.SEQUENCE.clear()
        context.destroyBean(bean)

        then: 'pre-destroy callbacks run superclass first too, as the compatibility kit asserts'
        events.SEQUENCE == ['Gate:PRE_DESTROY', 'Downstream:PRE_DESTROY', 'Weapon', 'Rocket']

        cleanup:
        context.close()
    }

    void 'test the definition of an intercepted bean exposes its callbacks in invocation order'() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        Class<?> events = context.classLoader.loadClass('event.chain.Events')
        Class<?> weaponType = context.classLoader.loadClass('event.chain.Weapon')
        Class<?> rocketType = context.classLoader.loadClass('event.chain.Rocket')
        events.proceed = true

        when:
        def bean = context.getBean(rocketType)
        BeanDefinition<?> definition = getBeanDefinition(context, rocketType.name)

        then: 'the callbacks of both kinds are listed, superclass first, and are not executable methods of the bean'
        definition.postConstructExecutableMethods*.methodName == ['postConstructWeapon', 'postConstruct']
        definition.postConstructExecutableMethods*.declaringType == [weaponType, rocketType]
        definition.preDestroyExecutableMethods*.methodName == ['preDestroyWeapon', 'preDestroy']
        definition.preDestroyExecutableMethods*.declaringType == [weaponType, rocketType]
        definition.postConstructExecutableMethods*.methodName == definition.postConstructMethods*.name
        definition.preDestroyExecutableMethods*.methodName == definition.preDestroyMethods*.name
        definition.postConstructExecutableMethods.every { it.hasAnnotation('jakarta.annotation.PostConstruct') }
        definition.preDestroyExecutableMethods.every { it.hasAnnotation('jakarta.annotation.PreDestroy') }
        definition.executableMethods.empty

        when: 'an interceptor can invoke a listed callback itself, private or not'
        events.SEQUENCE.clear()
        definition.postConstructExecutableMethods[1].invoke(bean)
        definition.preDestroyExecutableMethods[1].invoke(bean)

        then:
        events.SEQUENCE == ['Rocket', 'Rocket']

        cleanup:
        context.close()
    }
}

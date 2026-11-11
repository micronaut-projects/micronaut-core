package io.micronaut.aop.adapter

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Adapter
import io.micronaut.aop.InterceptorBinding
import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition

/**
 * An annotation declared with repeated {@code @InterceptorBinding} carries its bindings under the
 * {@code @InterceptorBindingDefinitions} container, and not as an {@code @InterceptorBinding} stereotype. Such
 * class level advice must not reach the generated adapter either.
 */
class AdapterRepeatedInterceptorBindingSpec extends AbstractTypeElementSpec {

    void 'class level advice declared with repeated interceptor bindings is not copied to the generated adapter'() {
        when:
        BeanDefinition definition = buildBeanDefinition('repeatedremoved.TrackedBean$ApplicationEventListener$onEvent1$Intercepted', '''
package repeatedremoved;

import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Singleton
@Tracked
class TrackedBean {
    @EventListener
    void onEvent(StartupEvent event) {
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.AROUND)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}
''')

        then:
        definition != null
        !definition.hasAnnotation('repeatedremoved.Tracked')
        !definition.hasStereotype('repeatedremoved.Tracked')
        definition.getAnnotationValuesByType(InterceptorBinding).isEmpty()
        definition.getAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS) == null
    }

    void 'class level advice declared with repeated interceptor bindings is applied to the declaring bean only'() {
        given:
        ApplicationContext context = buildContext('''
package repeatedruntime;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.AROUND)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Prototype
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements Interceptor<Object, Object> {
    static int instances;
    static final List<String> events = new ArrayList<>();

    private final int id = ++instances;

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        MethodInvocationContext<?, ?> methodContext = (MethodInvocationContext<?, ?>) context;
        Object target = methodContext.getTarget();
        String targetName = target instanceof TrackedBean ? "TrackedBean" : target.getClass().getSimpleName();
        events.add(id + ":" + methodContext.getKind() + ":" + targetName + "#" + methodContext.getMethodName());
        return context.proceed();
    }
}

class TheEvent {
}

@Singleton
@Tracked
class TrackedBean {
    final List<TheEvent> received = new ArrayList<>();

    @PostConstruct
    void init() {
    }

    @EventListener
    void onEvent(TheEvent event) {
        received.add(event);
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('repeatedruntime.TrackedBean')
        Class<?> interceptorType = context.classLoader.loadClass('repeatedruntime.TrackingInterceptor')

        when: 'the declaring bean and the adapter generated for its @EventListener method are both created'
        def bean = context.getBean(beanType)
        def adapterDefinition = context.getBeanDefinitions(ApplicationEventListener)
                .find { it.stringValue(Adapter, 'adaptedBean').orElse(null) == beanType.name }
        context.getBean(adapterDefinition)

        then: 'the adapter carries none of the declaring class advice'
        adapterDefinition != null
        !adapterDefinition.hasAnnotation('repeatedruntime.Tracked')
        adapterDefinition.getAnnotationValuesByType(InterceptorBinding).isEmpty()

        and: 'one interceptor instance was created, for the declaring bean'
        interceptorType.instances == 1
        interceptorType.events == ['1:POST_CONSTRUCT:TrackedBean#init']

        when:
        context.publishEvent(context.classLoader.loadClass('repeatedruntime.TheEvent').newInstance())

        then: 'the event is delivered once and intercepted once, on the declaring bean'
        bean.received.size() == 1
        interceptorType.instances == 1
        interceptorType.events == ['1:POST_CONSTRUCT:TrackedBean#init', '1:AROUND:TrackedBean#onEvent']

        cleanup:
        context.close()
    }
}

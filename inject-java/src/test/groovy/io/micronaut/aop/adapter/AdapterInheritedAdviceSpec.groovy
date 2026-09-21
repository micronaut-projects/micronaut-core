package io.micronaut.aop.adapter

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.InterceptorBinding
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition

/**
 * Compiles adapters in memory so the processor's handling of the advice inherited from the declaring class is
 * checked on the generated metadata itself.
 */
class AdapterInheritedAdviceSpec extends AbstractTypeElementSpec {

    void 'class level advice of the declaring bean is not copied to the generated adapter'() {
        when:
        BeanDefinition definition = buildBeanDefinition('adviceremoved.LoggedBean$ApplicationEventListener$onEvent1$Intercepted', '''
package adviceremoved;

import io.micronaut.aop.Around;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Singleton
@Logged
class LoggedBean {
    @EventListener
    void onEvent(StartupEvent event) {
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Logged {
}
''')

        then:
        definition != null
        !definition.hasAnnotation('adviceremoved.Logged')
        !definition.hasStereotype(AnnotationUtil.ANN_AROUND)
        definition.getAnnotationValuesByType(InterceptorBinding).isEmpty()
        definition.getAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS) == null
    }

    void 'advice declared on the adapted interface is kept under the interceptor bindings container'() {
        when:
        BeanDefinition definition = buildBeanDefinition('advicekept.LoggedBean$TracedListener$onEvent1$Intercepted', '''
package advicekept;

import io.micronaut.aop.Adapter;
import io.micronaut.aop.Around;
import io.micronaut.core.annotation.Indexed;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Singleton
@Logged
class LoggedBean {
    @TracedListenerMethod
    void onEvent(String event) {
    }
}

@Traced
interface TracedListener {
    void handle(String event);
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Adapter(TracedListener.class)
@Indexed(TracedListener.class)
@interface TracedListenerMethod {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Logged {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Traced {
}
''')

        then: 'only the interface advice remains'
        definition != null
        definition.hasAnnotation('advicekept.Traced')
        !definition.hasAnnotation('advicekept.Logged')

        and: 'its binding is readable from the container the writer resolves interceptors from'
        def container = definition.getAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)
        container != null
        definition.getAnnotationValuesByType(InterceptorBinding)*.stringValue()*.orElse(null) == ['advicekept.Traced']
    }

    void 'advice composed into an annotation on the adapted interface is kept when the declaring class shares it'() {
        when:
        BeanDefinition definition = buildBeanDefinition('advicecomposed.LoggedBean$ComposedListener$onEvent1$Intercepted', '''
package advicecomposed;

import io.micronaut.aop.Adapter;
import io.micronaut.aop.Around;
import io.micronaut.core.annotation.Indexed;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Singleton
@Logged
@Timed
class LoggedBean {
    @ComposedListenerMethod
    void onEvent(String event) {
    }
}

@Composed
interface ComposedListener {
    void handle(String event);
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Adapter(ComposedListener.class)
@Indexed(ComposedListener.class)
@interface ComposedListenerMethod {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Around
@interface Logged {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Logged
@interface Composed {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Timed {
}
''')

        then: 'the composed advice of the interface keeps its stereotype and binding'
        definition != null
        definition.hasAnnotation('advicecomposed.Composed')
        definition.hasStereotype('advicecomposed.Logged')
        definition.hasStereotype(AnnotationUtil.ANN_AROUND)
        definition.getAnnotationValuesByType(InterceptorBinding)*.stringValue()*.orElse(null) == ['advicecomposed.Logged']

        and: 'advice only the declaring class carries is still removed'
        !definition.hasAnnotation('advicecomposed.Timed')
    }
}

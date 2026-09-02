package io.micronaut.inject.injectionpoint.kind

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanResolutionContext
import io.micronaut.context.BeanResolutionCustomizer
import io.micronaut.inject.ArgumentInjectionPoint
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ConstructorInjectionPoint
import io.micronaut.inject.FieldInjectionPoint
import io.micronaut.inject.InjectionPoint
import spock.lang.Shared
import spock.lang.Specification

/**
 * The kind of an injection (field, constructor argument, method argument) must be
 * knowable from the public {@link InjectionPoint} hierarchy alone, both for a bean
 * that receives the {@link InjectionPoint} and for a {@link BeanResolutionCustomizer}
 * that reads the current {@link BeanResolutionContext.Segment}.
 */
class InjectionPointKindSpec extends Specification {

    @Shared
    List<BeanResolutionContext.Segment<?, ?>> kindBeanSegments = []

    @Shared
    ApplicationContext applicationContext

    @Shared
    KindConsumer consumer

    void setupSpec() {
        applicationContext = ApplicationContext.builder()
            .properties(['spec.name': 'InjectionPointKindSpec'])
            .beanResolutionCustomizer(new BeanResolutionCustomizer() {
                @Override
                boolean shouldDestroyDependentBeanAfterResolution(BeanResolutionContext resolutionContext, BeanDefinition<?> beanDefinition) {
                    if (beanDefinition.beanType == KindBean) {
                        resolutionContext.path.currentSegment().ifPresent(kindBeanSegments::add)
                    }
                    return false
                }
            })
            .start()
        consumer = applicationContext.getBean(KindConsumer)
    }

    void cleanupSpec() {
        applicationContext?.close()
    }

    void "a field injection point is a FieldInjectionPoint"() {
        given:
        InjectionPoint<?> ip = consumer.fromField.injectionPoint

        expect:
        ip instanceof FieldInjectionPoint
        ip instanceof ArgumentInjectionPoint
        ip.name == 'fromField'
        ip.declaringBean.beanType == KindConsumer
        ((FieldInjectionPoint) ip).type == KindBean
        ((FieldInjectionPoint) ip).asArgument().type == KindBean
        ((FieldInjectionPoint) ip).field.name == 'fromField'
    }

    void "a field injection point has no outer injection point"() {
        given:
        InjectionPoint<?> ip = consumer.fromField.injectionPoint

        expect:
        ((ArgumentInjectionPoint) ip).outerInjectionPoint == null
    }

    void "constructor and method injection points are arguments of their callable, not fields"() {
        given:
        InjectionPoint<?> ctor = consumer.fromConstructor.injectionPoint
        InjectionPoint<?> method = consumer.fromMethod.injectionPoint

        expect:
        !(ctor instanceof FieldInjectionPoint)
        ctor instanceof ArgumentInjectionPoint
        ((ArgumentInjectionPoint) ctor).outerInjectionPoint instanceof ConstructorInjectionPoint
        ((ArgumentInjectionPoint) ctor).argument.name == 'fromConstructor'

        !(method instanceof FieldInjectionPoint)
        method instanceof ArgumentInjectionPoint
        method.name == 'setFromMethod'
        ((ArgumentInjectionPoint) method).argument.name == 'fromMethod'
    }

    void "a resolution customizer can tell the kind of the current segment without internal classes"() {
        given:
        def injectionPoints = kindBeanSegments*.injectionPoint

        expect:
        injectionPoints.size() == 3
        injectionPoints.count { it instanceof FieldInjectionPoint } == 1
        injectionPoints.count { it instanceof ArgumentInjectionPoint && !(it instanceof FieldInjectionPoint) } == 2

        and:
        def field = injectionPoints.find { it instanceof FieldInjectionPoint }
        field.name == 'fromField'
        ((ArgumentInjectionPoint) field).outerInjectionPoint == null
    }
}

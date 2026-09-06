package io.micronaut.inject.injectionpoint.kind

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanResolutionContext
import io.micronaut.context.BeanResolutionCustomizer
import io.micronaut.inject.ArgumentInjectionPoint
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ConstructorInjectionPoint
import io.micronaut.inject.FieldInjectionPoint
import io.micronaut.inject.InjectionPoint
import io.micronaut.inject.MethodInjectionPoint
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
    List<BeanResolutionContext.Segment<?, ?>> dependencySegments = []

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
                    } else if (beanDefinition.beanType == KindDependency) {
                        resolutionContext.path.currentSegment().ifPresent(dependencySegments::add)
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

    void "a constructor argument injection point has the constructor as outer injection point"() {
        given:
        def outer = ((ArgumentInjectionPoint) consumer.fromConstructor.injectionPoint).outerInjectionPoint

        expect:
        outer instanceof ConstructorInjectionPoint
        !(outer instanceof MethodInjectionPoint)
        outer.declaringBean.beanType == KindConsumer
        outer.arguments*.name == ['fromConstructor']
    }

    void "a method argument injection point has its method as outer injection point"() {
        given:
        def outer = ((ArgumentInjectionPoint) consumer.fromMethod.injectionPoint).outerInjectionPoint

        expect:
        outer instanceof MethodInjectionPoint
        !(outer instanceof ConstructorInjectionPoint)
        outer.name == 'setFromMethod'
        outer.declaringBean.beanType == KindConsumer
        outer.arguments*.name == ['fromMethod']
        !((MethodInjectionPoint) outer).postConstructMethod
        !((MethodInjectionPoint) outer).preDestroyMethod
    }

    void "a factory method argument injection point has the factory method as outer injection point"() {
        given:
        def outers = dependencySegments*.injectionPoint*.outerInjectionPoint

        expect:
        // KindBean is a prototype created once per injection into KindConsumer
        outers.size() == 3
        outers.every { it instanceof ConstructorInjectionPoint }
        outers.every { it instanceof MethodInjectionPoint }
        outers*.name.unique() == ['kindBean']
        outers.every { it.declaringBean.beanType == KindBean }
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

    void "a resolution customizer can tell a constructor argument from a method argument by the outer injection point"() {
        given:
        def outers = kindBeanSegments*.injectionPoint
            .findAll { !(it instanceof FieldInjectionPoint) }
            .collect { ((ArgumentInjectionPoint) it).outerInjectionPoint }

        expect:
        outers.size() == 2
        outers.every { it != null }

        and:
        def constructor = outers.find { it instanceof ConstructorInjectionPoint }
        constructor != null
        constructor.arguments*.name == ['fromConstructor']

        and:
        def method = outers.find { !(it instanceof ConstructorInjectionPoint) }
        method instanceof MethodInjectionPoint
        method.name == 'setFromMethod'
        method.arguments*.name == ['fromMethod']
    }
}

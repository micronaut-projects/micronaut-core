package io.micronaut.inject.utils

import io.micronaut.context.beans.definition.BeanDefinitionInjectionPoint
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.BeanDefinitionInjectionPointResolver
import io.micronaut.inject.visitor.VisitorContext
import spock.lang.Specification

class BeanInjectionUtilsSpec extends Specification {

    void "delegates non-standard injection shapes to the visitor context resolver"() {
        given:
        ClassElement beanType = ClassElement.of("example.Bean")
        ClassElement requestedType = ClassElement.of("example.Custom")
        AnnotationMetadata annotationMetadata = AnnotationMetadata.EMPTY_METADATA
        def resolved = new BeanDefinitionInjectionPoint.BeansInjectionPoint<ClassElement>(
            requestedType,
            annotationMetadata,
            ClassElement.of(String)
        )
        BeanDefinitionInjectionPointResolver resolver = Mock()
        VisitorContext visitorContext = Stub() {
            getBeanDefinitionInjectionPointResolver() >> resolver
        }

        when:
        def result = BeanInjectionUtils.getInjectionPoint(
            beanType,
            requestedType,
            annotationMetadata,
            "custom",
            visitorContext
        )

        then:
        1 * resolver.resolve(beanType, requestedType, annotationMetadata, "custom", visitorContext) >> Optional.of(resolved)
        result.is(resolved)
    }

    void "uses the standard bean injection point when the resolver does not handle a type"() {
        given:
        ClassElement requestedType = ClassElement.of("example.Custom")
        VisitorContext visitorContext = Stub()

        when:
        def result = BeanInjectionUtils.getInjectionPoint(
            ClassElement.of("example.Bean"),
            requestedType,
            AnnotationMetadata.EMPTY_METADATA,
            "custom",
            visitorContext
        )

        then:
        result instanceof BeanDefinitionInjectionPoint.BeanInjectionPoint
        result.type().is(requestedType)
    }
}

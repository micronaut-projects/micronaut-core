package io.micronaut.inject.qualifiers

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.BeanType
import spock.lang.Specification

class InterceptorBindingQualifierSpec extends Specification {

    void "an interceptor binding by the members of the intercept point qualifies"() {
        expect:
        qualifierFor(binding("north")).doesQualify(Object, candidate(binding("north")))
    }

    void "an interceptor binding by other members does not qualify"() {
        expect:
        !qualifierFor(binding("south")).doesQualify(Object, candidate(binding("north")))
    }

    void "an interceptor binding by no members qualifies whatever the intercept point binds by"() {
        expect:
        qualifierFor(binding("south")).doesQualify(Object, candidate(binding(null)))
    }

    void "the memberless binding of an intercept point does not discard its members"() {
        given: "an intercept point recording both a memberless binding and one binding by members"
        def qualifier = new InterceptorBindingQualifier([binding("south"), binding(null)])

        expect:
        !qualifier.doesQualify(Object, candidate(binding("north")))
        qualifier.doesQualify(Object, candidate(binding("south")))
    }

    private static InterceptorBindingQualifier<?> qualifierFor(AnnotationValue<?>... interceptPoint) {
        new InterceptorBindingQualifier(interceptPoint as List)
    }

    private static AnnotationValue<?> binding(String zone) {
        def builder = AnnotationValue.builder(AnnotationUtil.ANN_INTERCEPTOR_BINDING).value("test.Zone")
        if (zone != null) {
            builder.member(InterceptorBindingQualifier.META_BINDING_VALUES,
                    AnnotationValue.builder("test.Zone").value(zone).build())
        }
        builder.build()
    }

    private BeanType<?> candidate(AnnotationValue<?> interceptorBinding) {
        def metadata = Stub(AnnotationMetadata) {
            getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING) >> [interceptorBinding]
        }
        Stub(BeanType) {
            getAnnotationMetadata() >> metadata
            getBeanType() >> Object
        }
    }
}

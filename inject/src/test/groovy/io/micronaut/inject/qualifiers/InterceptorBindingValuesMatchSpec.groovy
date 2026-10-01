package io.micronaut.inject.qualifiers

import io.micronaut.core.annotation.AnnotationValue
import spock.lang.Specification

class InterceptorBindingValuesMatchSpec extends Specification {

    void "test an interceptor binding by no members matches any intercept point"() {
        expect:
        InterceptorBindingQualifier.bindingValuesMatch(null, null)
        InterceptorBindingQualifier.bindingValuesMatch(null, bindingValues(level: "INFO"))
    }

    void "test an interceptor binding by members matches the members meaning the same"() {
        given:
        def defaults = [level: "INFO"] as Map<CharSequence, Object>
        def interceptor = new AnnotationValue("test.Binding", [level: "INFO"] as Map<CharSequence, Object>, defaults)
        def omitted = new AnnotationValue("test.Binding", [:] as Map<CharSequence, Object>, defaults)
        def different = new AnnotationValue("test.Binding", [level: "DEBUG"] as Map<CharSequence, Object>, defaults)

        expect: "an intercept point leaving the member at its default is the same binding"
        InterceptorBindingQualifier.bindingValuesMatch(interceptor, omitted)

        and: "one binding the member to something else is not"
        !InterceptorBindingQualifier.bindingValuesMatch(interceptor, different)

        and: "nor is an intercept point binding by no members at all"
        !InterceptorBindingQualifier.bindingValuesMatch(interceptor, null)
    }

    private static AnnotationValue<?> bindingValues(Map<CharSequence, Object> values) {
        new AnnotationValue("test.Binding", values)
    }
}

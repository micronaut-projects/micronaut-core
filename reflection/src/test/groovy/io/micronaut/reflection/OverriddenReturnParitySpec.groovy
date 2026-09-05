package io.micronaut.reflection

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import spock.lang.PendingFeature
import spock.lang.Specification

/**
 * The annotations of a method that overrides a declaration, and of its return value, described either way.
 *
 * <p>Each case states what the processors answer today before comparing the two, so that a description built
 * reflectively is held to what a generated one actually says rather than to what it is assumed to say, and so
 * that a change on the generated side is seen here rather than silently followed.</p>
 */
class OverriddenReturnParitySpec extends Specification {

    BeanIntrospection<OverriddenReturn> generated = BeanIntrospector.SHARED.getIntrospection(OverriddenReturn)
    BeanIntrospection<OverriddenReturn> reflective = ReflectionBeanIntrospection.of(OverriddenReturn)

    private static List<String> tags(AnnotationMetadata metadata) {
        return metadata.getAnnotationValuesByType(Tag)*.stringValue()*.orElse(null).toSorted()
    }

    private static AnnotationMetadata methodOf(BeanIntrospection<?> introspection, String name) {
        return introspection.getBeanMethods().find { it.name == name }.getAnnotationMetadata()
    }

    private static AnnotationMetadata returnOf(BeanIntrospection<?> introspection, String name) {
        return introspection.getBeanMethods().find { it.name == name }.getReturnType().getAnnotationMetadata()
    }

    void "the method #method carries what the override declares, described either way"() {
        expect: "what a generated description answers today: the annotation of the override, and not the one the"
        and: "interface or the super class declares on the method it overrides"
        tags(methodOf(generated, method)) == ["from-impl"]

        and: "and a reflective description answers the same"
        tags(methodOf(reflective, method)) == tags(methodOf(generated, method))

        where:
        method << ["place", "describe"]
    }

    @PendingFeature(reason = "a reflective description puts the annotations of the method on its return value, where a generated one leaves the return value to the annotations of the type returned")
    void "the return value of #method carries no annotation of the method, described either way"() {
        expect: "what a generated description answers today: an annotation written on the method is on the method,"
        and: "and the return value carries only what is written on the type it returns"
        tags(returnOf(generated, method)) == []

        and: "and a reflective description answers the same, rather than repeating the method's annotations on it"
        tags(returnOf(reflective, method)) == tags(returnOf(generated, method))

        where:
        method << ["place", "describe"]
    }
}

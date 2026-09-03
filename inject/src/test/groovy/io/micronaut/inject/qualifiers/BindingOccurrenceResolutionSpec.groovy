package io.micronaut.inject.qualifiers

import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import spock.lang.Specification

import java.lang.annotation.RetentionPolicy

/**
 * The occurrences an interceptor binding compares members with come from the metadata it was declared on, either
 * declared there or composed by another annotation and retained on it.
 *
 * <p>Metadata compiled before the binding annotation became retainable carries a copy of its occurrence in a
 * reserved {@code $bindingValues} member instead, and is read from there. Applications are not recompiled when
 * the framework is upgraded, so both shapes have to resolve, and a binding written by either compiler has to
 * match one written by the other.</p>
 */
class BindingOccurrenceResolutionSpec extends Specification {

    private static final String BINDING = "io.micronaut.aop.InterceptorBinding"
    private static final String ZONE = "test.Zone"

    void "a binding written before the annotation was retainable is read from its copy"() {
        given: "the shape an earlier compiler wrote: the members nested in the binding"
        def binding = AnnotationValue.builder(BINDING)
                .value(ZONE)
                .member("bindMembers", true)
                .member(InterceptorBindingQualifier.META_BINDING_VALUES, zone("north"))
                .build()

        expect: "the copy is the occurrence, with no metadata needed to find it"
        InterceptorBindingQualifier.resolveBoundOccurrences(binding, null) == [zone("north")]
    }

    void "a binding written now is read from the occurrence on the element"() {
        given:
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation(ZONE, [value: "north"] as Map<CharSequence, Object>, RetentionPolicy.RUNTIME)

        expect:
        InterceptorBindingQualifier.resolveBoundOccurrences(binding(), metadata) == [zone("north")]
    }

    void "an occurrence composed by another annotation is found through the retained tree"() {
        given: "an annotation composing the binding annotation, retaining the occurrence it introduced"
        def metadata = new MutableAnnotationMetadata()
        def composed = [
                (AnnotationUtil.STEREOTYPES_MEMBER): [zone("south")] as AnnotationValue[]
        ] as Map<CharSequence, Object>
        metadata.addDeclaredAnnotation("test.SouthZone", composed, RetentionPolicy.RUNTIME)
        metadata.addDeclaredStereotype(["test.SouthZone"], ZONE, [value: "south"] as Map<CharSequence, Object>, RetentionPolicy.RUNTIME)

        expect:
        InterceptorBindingQualifier.resolveBoundOccurrences(binding(), metadata) == [zone("south")]
    }

    void "members marked non binding take no part in the comparison"() {
        given:
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation(ZONE, [
                value                              : "north",
                debug                              : true,
                (AnnotationUtil.NON_BINDING_ATTRIBUTE): ["debug", AnnotationUtil.NON_BINDING_ATTRIBUTE] as String[]
        ] as Map<CharSequence, Object>, RetentionPolicy.RUNTIME)

        expect:
        InterceptorBindingQualifier.resolveBoundOccurrences(binding(), metadata) == [zone("north")]
    }

    void "a binding that does not bind members is compared by name"() {
        given:
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation(ZONE, [value: "north"] as Map<CharSequence, Object>, RetentionPolicy.RUNTIME)

        expect: "no occurrences means no member comparison, so the binding applies wherever its annotation does"
        InterceptorBindingQualifier.resolveBoundOccurrences(
                AnnotationValue.builder(BINDING).value(ZONE).build(), metadata) == null
    }

    void "an occurrence read from a copy matches one read from the element"() {
        given: "one side compiled before the change and the other after"
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation(ZONE, [value: "north"] as Map<CharSequence, Object>, RetentionPolicy.RUNTIME)
        def written = InterceptorBindingQualifier.resolveBoundOccurrences(binding(), metadata)
        def copied = InterceptorBindingQualifier.resolveBoundOccurrences(
                binding().mutate().member(InterceptorBindingQualifier.META_BINDING_VALUES, zone("north")).build(), null)

        expect:
        InterceptorBindingQualifier.anyMatch(written, copied)

        and: "and does not match one bound to something else"
        !InterceptorBindingQualifier.anyMatch(written, [zone("south")])
    }

    private static AnnotationValue<?> binding() {
        AnnotationValue.builder(BINDING).value(ZONE).member("bindMembers", true).build()
    }

    private static AnnotationValue<?> zone(String value) {
        new AnnotationValue(ZONE, [value: value] as Map<CharSequence, Object>)
    }
}

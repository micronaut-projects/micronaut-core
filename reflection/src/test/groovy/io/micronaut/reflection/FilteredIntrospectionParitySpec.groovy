package io.micronaut.reflection

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import spock.lang.Specification

/**
 * The {@link io.micronaut.core.annotation.Introspected} members that say which properties are described, and
 * whether the properties carry their annotations, applied by a reflective description the way the processor
 * applies them to a generated one.
 */
class FilteredIntrospectionParitySpec extends Specification {

    /**
     * The properties of a reflective description without the one the Groovy compiler adds to every class, which
     * a generated description leaves out.
     */
    private static List<String> names(BeanIntrospection<?> introspection) {
        return introspection.getBeanProperties()*.name.findAll { it != "metaClass" }.toSorted()
    }

    void "#type.simpleName describes the same properties reflectively as it does generated"() {
        given:
        def generated = BeanIntrospector.SHARED.getIntrospection(type)
        def reflective = ReflectionBeanIntrospection.of(type)

        expect:
        names(reflective) == names(generated)

        and:
        names(generated) == described

        where:
        type                                          || described
        FilteredParityBeans.Excludes                  || ["kept", "secret"]
        FilteredParityBeans.Includes                  || ["kept"]
        FilteredParityBeans.ExcludedAnnotations       || ["kept", "password"]
        FilteredParityBeans.NoMetadata                || ["kept", "secret"]
    }

    void "a type naming includedAnnotations describes every property, as a generated description of it does"() {
        given: "the member reads as a property filter, but the processor applies it to package scanning alone"
        def generated = BeanIntrospector.SHARED.getIntrospection(FilteredParityBeans.IncludedAnnotations)
        def reflective = ReflectionBeanIntrospection.of(FilteredParityBeans.IncludedAnnotations)

        expect:
        names(reflective) == names(generated)
        names(generated) == ["kept", "password", "secret"]
    }

    void "a type asking for no annotation metadata carries none on its properties, reflectively as generated"() {
        given:
        def generated = BeanIntrospector.SHARED.getIntrospection(FilteredParityBeans.NoMetadata)
        def reflective = ReflectionBeanIntrospection.of(FilteredParityBeans.NoMetadata)

        expect:
        reflective.getRequiredProperty("secret", String).annotationMetadata.annotationNames ==
            generated.getRequiredProperty("secret", String).annotationMetadata.annotationNames

        and: "which is nothing, where the same property of a type that asks for its metadata carries @Hidden"
        generated.getRequiredProperty("secret", String).annotationMetadata.isEmpty()
        BeanIntrospector.SHARED.getIntrospection(FilteredParityBeans.Excludes)
            .getRequiredProperty("secret", String).annotationMetadata.hasAnnotation(Hidden)
    }
}

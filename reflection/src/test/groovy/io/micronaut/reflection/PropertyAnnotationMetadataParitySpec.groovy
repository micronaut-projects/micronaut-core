package io.micronaut.reflection

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import spock.lang.Specification

class PropertyAnnotationMetadataParitySpec extends Specification {

    BeanIntrospection<PropertyAnnotationSites> generated = BeanIntrospection.getIntrospection(PropertyAnnotationSites)
    BeanIntrospection<PropertyAnnotationSites> reflective = ReflectionBeanIntrospection.of(PropertyAnnotationSites)

    void "a property annotation selected from conflicting access sites is the same"() {
        given:
        def read = { BeanIntrospection<PropertyAnnotationSites> introspection ->
            introspection.getRequiredProperty("conflicting", String).annotationMetadata
                .stringValue(PropertyAnnotationSites.SiteValue).orElse(null)
        }

        expect:
        read(reflective) == read(generated)

        and: "the compile-time property model selects the getter"
        read(generated) == "getter"
    }

    void "a setter parameter annotation is not promoted to property metadata"() {
        expect:
        !generated.getRequiredProperty("parameterOnly", String).annotationMetadata
            .hasAnnotation(PropertyAnnotationSites.SiteValue)

        and:
        reflective.getRequiredProperty("parameterOnly", String).annotationMetadata
            .hasAnnotation(PropertyAnnotationSites.SiteValue) ==
            generated.getRequiredProperty("parameterOnly", String).annotationMetadata
                .hasAnnotation(PropertyAnnotationSites.SiteValue)
    }

    void "repeatable annotations from separate access sites are collected the same"() {
        given:
        def read = { BeanIntrospection<PropertyAnnotationSites> introspection ->
            introspection.getRequiredProperty("repeated", String).annotationMetadata
                .getAnnotationValuesByType(Tag)*.stringValue()*.orElse(null)
        }

        expect:
        read(reflective) == read(generated)

        and: "the compile-time property carries only the occurrence of its selected getter"
        read(generated) == ["getter"]
    }

    void "the ordinary queries of a declared metadata view expose the same annotations"() {
        given:
        def read = { BeanIntrospection<PropertyAnnotationSites> introspection ->
            introspection.getRequiredProperty("declared", String).annotationMetadata
                .declaredMetadata.annotationNames.toSorted()
        }
        def expected = [PropertyAnnotationSites.SiteValue.name]

        expect:
        generated.getRequiredProperty("declared", String).annotationMetadata
            .declaredAnnotationNames.toSorted() == expected
        read(generated) == expected

        and:
        read(reflective) == read(generated)
    }
}

@Introspected(classes = PropertyAnnotationSites)
class PropertyAnnotationSitesIntrospection {
}

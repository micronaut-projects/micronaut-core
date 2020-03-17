package io.micronaut.jackson.modules

import com.fasterxml.jackson.annotation.JsonInclude
import io.micronaut.core.annotation.Introspected
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

class BeanIntrospectionUtilsSpec extends Specification {

    @Unroll("#description")
    void "test annotation value parsing"(Object bean, String property, JsonInclude.Include expected, String description) {

        when:
        Class annotationClass = JsonInclude.class;
        Class annotationValueClass = JsonInclude.Include.class;
        Object object = BeanIntrospectionUtils.parseAnnotationValueForProperty(property, bean, annotationClass, annotationValueClass)

        then:
        noExceptionThrown()
        object == expected

        where:
        bean                         | property    | expected                     | description
        new GuideOnlyProperties()    | "name"      | JsonInclude.Include.ALWAYS   | 'GuideOnlyProperties::name property annotation is used'
        new GuideOnlyProperties()    | "author"    | JsonInclude.Include.NON_NULL | 'GuideOnlyProperties::author property annotation is used'
        new GuideOnlyProperties()    | "publisher" | null                         | 'GuideOnlyProperties::publisher property is not annotated, null is returned'
        new Guide()                  | "name"      | JsonInclude.Include.ALWAYS   | 'Guide::name property annotation takes precedence over class annotation'
        new Guide()                  | "author"    | JsonInclude.Include.NON_NULL | 'Guide::author property not annotated uses class annotation'
        new GuideTypeAnnotated()     | "name"      | JsonInclude.Include.ALWAYS   | 'GuideTypeAnnotated::name property not annotated uses class annotation'
        new GuideTypeAnnotated()     | "author"    | JsonInclude.Include.ALWAYS   | 'GuideTypeAnnotated::author property not annotated uses class annotation'
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Introspected
    static class Guide {
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String name
        String author
    }

    @Introspected
    static class GuideOnlyProperties {
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String name

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String author
        String publisher
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Introspected
    static class GuideTypeAnnotated {
        String name
        String author
    }
}

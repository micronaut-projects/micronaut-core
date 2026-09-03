package io.micronaut.inject.annotation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationValue
import spock.lang.Unroll

class RetainableSpec extends AbstractBeanDefinitionSpec {

    @Unroll
    void "a retainable composed annotation is attributed to the annotation that introduced it (#kind)"() {
        given:
        def annotationMetadata = buildTypeAnnotationMetadata('retainspec.Test', '''
package retainspec;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.inject.annotation.Limit;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Limit(min = 5)
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedA {
    @AliasFor(annotation = Limit.class, member = "min", applyDefault = true)
    int min() default 5
}

@Limit(max = 50)
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedB {
    @AliasFor(annotation = Limit.class, member = "max", applyDefault = true)
    int max() default 50
}

@ComposedA(min = 3)
@ComposedB(max = 9)
class Test {
}
''')
        if (kind == "written") {
            annotationMetadata = writeAndLoadMetadata('retainspec.Test', annotationMetadata)
        }

        expect: "the flat index keeps one Limit for both composing annotations"
        annotationMetadata.getAnnotationNamesByStereotype(Limit.name) as Set ==
                ["retainspec.ComposedA", "retainspec.ComposedB"] as Set
        annotationMetadata.getAnnotation(Limit.name) != null

        and: "the retained tree keeps each occurrence apart"
        limitOf(annotationMetadata.getAnnotation("retainspec.ComposedA")) == [[min: 3]]
        limitOf(annotationMetadata.getAnnotation("retainspec.ComposedB")) == [[max: 9]]

        where:
        kind << ["compiled", "written"]
    }

    /**
     * The cascade lives in the shared builder, but each language frontend resolves an annotation type by name
     * differently, and that resolution is what carries an override down the subtree.
     */
    void "a transitive override cascades to what the intermediate annotation composes"() {
        given:
        def annotationMetadata = buildTypeAnnotationMetadata('cascadespec.Test', '''
package cascadespec

import io.micronaut.context.annotation.AliasFor
import io.micronaut.inject.annotation.Limit
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Inner(min = 1)
@Retention(RetentionPolicy.RUNTIME)
@interface Outer {
    @AliasFor(annotationName = "cascadespec.Inner", member = "min", applyDefault = true)
    int shortest() default 1
}

@Limit(min = 2)
@Retention(RetentionPolicy.RUNTIME)
@interface Inner {
    @AliasFor(annotation = Limit.class, member = "min", applyDefault = true)
    int min() default 2
}

@Outer(shortest = 3)
class Test {
}
''')

        when:
        def inner = annotationMetadata.getAnnotation("cascadespec.Outer")
                .getStereotypes()
                .find { it.getAnnotationName() == "cascadespec.Inner" }

        then: "the override reaches the intermediate annotation"
        inner.getValues() == [min: 3]

        and: "and cascades to what that annotation composes"
        limitOf(inner) == [[min: 3]]

        and: "the flat index agrees"
        annotationMetadata.getAnnotation(Limit.name).getValues() == [min: 3]
    }

    private static List<Map> limitOf(AnnotationValue<?> annotationValue) {
        annotationValue.getStereotypes()
                .findAll { it.getAnnotationName() == Limit.name }
                .collect { it.getValues() }
    }
}

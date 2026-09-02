package io.micronaut.inject.annotation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationValue
import spock.lang.Unroll

class RetainStereotypesSpec extends AbstractBeanDefinitionSpec {

    @Unroll
    void "@RetainStereotypes attributes each composing occurrence to the annotation that introduced it (#kind)"() {
        given:
        def annotationMetadata = buildTypeAnnotationMetadata('retainspec.Test', '''
package retainspec;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.RetainStereotypes;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@RetainStereotypes
@Size(min = 5)
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedA {
    @AliasFor(annotation = Size.class, member = "min", applyDefault = true)
    int min() default 5
}

@RetainStereotypes
@Size(max = 50)
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedB {
    @AliasFor(annotation = Size.class, member = "max", applyDefault = true)
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

        expect: "the flat index cannot tell the two Size occurrences apart"
        annotationMetadata.getAnnotationNamesByStereotype('jakarta.validation.constraints.Size$List') as Set ==
                ["retainspec.ComposedA", "retainspec.ComposedB"] as Set

        and: "the retained tree can"
        sizeOf(annotationMetadata.getAnnotation("retainspec.ComposedA")) == [[min: 3]]
        sizeOf(annotationMetadata.getAnnotation("retainspec.ComposedB")) == [[max: 9]]

        where:
        kind << ["compiled", "written"]
    }

    private static List<Map> sizeOf(AnnotationValue<?> annotationValue) {
        annotationValue.getStereotypes()
                .findAll { it.getAnnotationName() == "jakarta.validation.constraints.Size" }
                .collect { it.getValues() }
    }
}

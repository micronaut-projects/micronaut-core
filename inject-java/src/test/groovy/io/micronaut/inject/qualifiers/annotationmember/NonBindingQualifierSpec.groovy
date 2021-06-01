package io.micronaut.inject.qualifiers.annotationmember

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition

import jakarta.inject.Qualifier

class NonBindingQualifierSpec extends AbstractTypeElementSpec {

    void 'test @NonBinding annotation populates nonBinding meta-member'() {
        given:
        def definition = buildBeanDefinition('annotationmember.Test', '''
package annotationmember;

import io.micronaut.context.annotation.NonBinding;
import jakarta.inject.Qualifier;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


@Cylinders(value = 8, description="test")
class Test {
}

@Qualifier
@Retention(RUNTIME)
@interface Cylinders {
    int value();

    @NonBinding
    String description() default "";
    
}

''')

        expect:
        definition != null
        definition
                .getAnnotationMetadata()
                .getAnnotation(AnnotationUtil.QUALIFIER)
                .stringValues("nonBinding") as Set == ['description'] as Set
        definition
            .annotationMetadata
            .getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER).get() == 'annotationmember.Cylinders'
    }
}

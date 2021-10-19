package io.micronaut.inject.qualifiers.annotationmember

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition

import jakarta.inject.Qualifier

class NonBindingQualifierSpec extends AbstractTypeElementSpec {

    void "test qualify by annotation member"() {
        given:"Some beans and a qualifier that has annotation members"
        def context = buildContext('''
package anntoationmembertest;

import io.micronaut.context.annotation.NonBinding;
import jakarta.inject.*;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Singleton
class Test {
    @Cylinders(6) public Engine v6;
    @Cylinders(8) public Engine v8;
}

@Singleton
@Cylinders(value = 6, description = "6-cylinder V6 engine")
class V6Engine implements Engine { 

    @Override
    public int getCylinders() {
        return 6;
    }
}

@Singleton
@Cylinders(value = 8, description = "8-cylinder V8 engine") 
class V8Engine implements Engine { 
    @Override
    public int getCylinders() {
        return 8;
    }
}

interface Engine {
    int getCylinders();
}

@Qualifier 
@Retention(RUNTIME)
@interface Cylinders {
    int value();

    @NonBinding 
    String description() default "";
}
''')
        def bean = getBean(context, 'anntoationmembertest.Test')

        expect:"the value is used for qualifying but not the description"
        bean.v8.cylinders == 8
        bean.v6.cylinders == 6

        cleanup:
        context.close()
    }

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

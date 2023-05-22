package io.micronaut.inject.annotation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition

import java.lang.annotation.Native

class RetentionSpec extends AbstractBeanDefinitionSpec {
    void "test source retention annotations are not retained"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''
package test;

@jakarta.inject.Singleton
class Test {


    @jakarta.inject.Inject
    @java.lang.annotation.Native
    protected String someField;

}
''')

        expect:
        !definition.injectedFields.first().annotationMetadata.hasAnnotation(Native)
    }

    void "test missing retention or CLASS retention annotations are excluded from runtime"() {
        given:
            BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import jakarta.inject.*;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Missing1
@Missing2
@Missing3
@NotMissing1
@NotMissing5
@Singleton
class Test {

}

@Inherited
@Missing4
@Documented
@NotMissing2
@Retention(RetentionPolicy.SOURCE)
@Target([ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD])
@interface Missing1 {
}

@Inherited
@Documented
@NotMissing3
@Retention(RetentionPolicy.CLASS)
@Target([ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD])
@interface Missing2 {
}

@Inherited
@NotMissing4
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target([ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD])
@interface Missing3 {
}

@Inherited
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target([ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD])
@interface Missing4 {
}

@Inherited
@Missing1
@Missing2
@Missing3
@NotMissing6
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD])
@interface NotMissing1 {
}

@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD])
@interface NotMissing2 {
}

@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD])
@interface NotMissing3 {
}

@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD])
@interface NotMissing4 {
}

@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD])
@interface NotMissing5 {
}

@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD])
@interface NotMissing6 {
}

''')
            Set<String> allAnnotations = definition.getAnnotationNames() + definition.getStereotypeAnnotationNames()
        expect:
            allAnnotations == ["jakarta.inject.Singleton", "jakarta.inject.Scope", "test.NotMissing1", "test.NotMissing2", "test.NotMissing3", "test.NotMissing4", "test.NotMissing5", "test.NotMissing6"] as Set
    }
}

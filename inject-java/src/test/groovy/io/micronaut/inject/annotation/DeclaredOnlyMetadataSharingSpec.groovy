package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.BeanDefinition

class DeclaredOnlyMetadataSharingSpec extends AbstractTypeElementSpec {

    void "metadata of an element that inherits nothing shares its declared and all annotations"() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('test.Test', '''
package test;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@interface Marker {
    String value() default "";
}

@jakarta.inject.Singleton
@Marker("type")
class Test {
}
''')
        AnnotationMetadata metadata = definition.annotationMetadata.targetAnnotationMetadata

        expect:
        metadata instanceof DefaultAnnotationMetadata
        metadata.getDeclaredMetadata().is(metadata)
        metadata.stringValue('test.Marker').get() == 'type'
        metadata.hasDeclaredAnnotation('test.Marker')
        metadata.hasStereotype('jakarta.inject.Scope')
        metadata.hasDeclaredStereotype('jakarta.inject.Scope')
    }

    void "metadata of an element that inherits annotations keeps separate declared annotations"() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('test.Test', '''
package test;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Inherited
@interface Marker {
    String value() default "";
}

@Marker("parent")
class Parent {
}

@jakarta.inject.Singleton
class Test extends Parent {
}
''')
        AnnotationMetadata metadata = definition.annotationMetadata.targetAnnotationMetadata

        expect:
        metadata.hasAnnotation('test.Marker')
        !metadata.hasDeclaredAnnotation('test.Marker')
        !metadata.getDeclaredMetadata().is(metadata)
        !metadata.getDeclaredMetadata().hasAnnotation('test.Marker')
    }
}

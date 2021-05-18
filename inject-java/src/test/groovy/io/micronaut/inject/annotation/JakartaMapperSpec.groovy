package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Scope
import javax.inject.Singleton

class JakartaMapperSpec extends AbstractTypeElementSpec {

    void "test annotation mapper map stereotypes correctly"() {
        def metadata = buildTypeAnnotationMetadata('''
package test;

@jakarta.inject.Singleton
@jakarta.inject.Named("test")
class Test {

}
''')

        expect:
        metadata != null
        metadata.hasDeclaredAnnotation(Singleton)
        metadata.hasDeclaredStereotype(Scope)
        metadata.getAnnotationNamesByStereotype(Scope.class).size() == 1
        metadata.getAnnotationNamesByStereotype(Scope.class).contains(Singleton.name)
        metadata.getAnnotationNamesByStereotype(Qualifier.class).size() == 1
        metadata.getAnnotationNamesByStereotype(Qualifier.class).contains(Named.name)
    }

    void "test annotation mapper map stereotypes correctly with meta annotations"() {
        def metadata = buildTypeAnnotationMetadata('''
package test;

import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.*;

@Meta
class Test {

}

@jakarta.inject.Singleton
@jakarta.inject.Named("test")
@Retention(RUNTIME)
@interface Meta {

}
''')

        expect:
        metadata != null
        metadata.hasDeclaredStereotype(Singleton)
        !metadata.hasStereotype(jakarta.inject.Singleton)
        metadata.hasDeclaredStereotype(Scope)
        metadata.getAnnotationNamesByStereotype(Scope.class).size() == 1
        metadata.getAnnotationNamesByStereotype(Scope.class).contains(Singleton.name)
        metadata.getAnnotationNamesByStereotype(Qualifier.class).size() == 1
        metadata.getAnnotationNamesByStereotype(Qualifier.class).contains(Named.name)
    }
}

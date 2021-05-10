package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

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
        metadata.getAnnotationNamesByStereotype(Qualifier.class).size() == 1
    }
}

package io.micronaut.inject.annotation

import io.micronaut.inject.AbstractTypeElementSpec

import javax.inject.Scope
import javax.inject.Singleton

class JakartaMapperSpec extends AbstractTypeElementSpec {

    void "test annotation mapper map stereotypes correctly"() {
        def metadata = buildTypeAnnotationMetadata('''
package test;

@jakarta.inject.Singleton
class Test {

}
''')

        expect:
        metadata != null
        metadata.hasDeclaredAnnotation(Singleton)
        metadata.hasDeclaredStereotype(Scope)
    }
}

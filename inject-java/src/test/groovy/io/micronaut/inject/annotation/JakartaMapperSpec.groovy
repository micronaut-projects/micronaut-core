package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationUtil

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
        metadata.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)
        metadata.hasDeclaredStereotype(AnnotationUtil.SCOPE)
        metadata.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 1
        metadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER).size() == 1
    }

    void "test factory methods"() {
        def metadata = buildMethodAnnotationMetadata('''
package test;

@io.micronaut.context.annotation.Factory
class TestFactory {
    
    @jakarta.inject.Singleton
    Test test() {
        return new Test();
    }

}

class Test {

}
''', 'test')

        expect:
        metadata != null
        metadata.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)
        metadata.hasDeclaredStereotype(AnnotationUtil.SCOPE)
    }
}

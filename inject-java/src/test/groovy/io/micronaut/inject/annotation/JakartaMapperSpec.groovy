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
        metadata.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).contains(AnnotationUtil.SINGLETON)
        metadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER).size() == 1
        metadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER).contains(AnnotationUtil.NAMED)

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
        metadata.hasDeclaredStereotype(AnnotationUtil.SINGLETON)
        !metadata.hasStereotype(jakarta.inject.Singleton)
        metadata.hasDeclaredStereotype(AnnotationUtil.SCOPE)
        metadata.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 1
        metadata.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).contains(AnnotationUtil.SINGLETON)
        !metadata.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).contains("test.Meta")
        metadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER).size() == 1
        metadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER).contains(AnnotationUtil.NAMED)
        !metadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER).contains("test.Meta")
    }
}

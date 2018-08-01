package io.micronaut.inject.annotation

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.AbstractTypeElementSpec

import javax.inject.Named

class ArgumentAnnotationMetadataSpec extends AbstractTypeElementSpec {

    void "test basic argument metadata"() {
        given:
        AnnotationMetadata metadata = buildFieldAnnotationMetadata('''
package test;

@javax.inject.Singleton
class Test {

    void test(@javax.inject.Named("foo") String id) {
    
    }
}
''', 'test', 'id')

        expect:
        metadata != null
        !metadata.empty
        metadata.hasDeclaredAnnotation(Named)
        metadata.getValue(Named).get() == "foo"
    }

    void "test argument metadata inheritance"() {
        given:
        AnnotationMetadata metadata = buildFieldAnnotationMetadata('''
package test;

@javax.inject.Singleton
class Test implements TestApi {

    @javax.annotation.PostConstruct
    @java.lang.Override
    public void test(String id) {
    
    }
}

interface TestApi {

    void test(@javax.inject.Named("foo") String id);

}
''', 'test', 'id')

        expect:
        metadata != null
        !metadata.empty
        !metadata.hasDeclaredAnnotation(Named)
        metadata.hasAnnotation(Named)
        metadata.getValue(Named).get() == "foo"
    }

}

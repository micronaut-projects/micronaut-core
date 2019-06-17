package io.micronaut.inject.annotation

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.AbstractTypeElementSpec

import javax.inject.Named

class AnnotationRemapperSpec extends AbstractTypeElementSpec {

    void "test nullable"() {
        given:
        AnnotationMetadata metadata = buildFieldAnnotationMetadata('''
package test;

import edu.umd.cs.findbugs.annotations.Nullable;
@javax.inject.Singleton
class Test {

    void test(@Nullable String id) {
    
    }
}
''', 'test', 'id')

        expect:
        metadata != null
        !metadata.empty
        metadata.hasAnnotation(AnnotationUtil.NULLABLE)
    }

    void "test nonnull"() {
        given:
        AnnotationMetadata metadata = buildFieldAnnotationMetadata('''
package test;

import edu.umd.cs.findbugs.annotations.NonNull;
@javax.inject.Singleton
class Test {

    void test(@NonNull String id) {
    
    }
}
''', 'test', 'id')

        expect:
        metadata != null
        !metadata.empty
        metadata.hasAnnotation(AnnotationUtil.NON_NULL)
    }
}

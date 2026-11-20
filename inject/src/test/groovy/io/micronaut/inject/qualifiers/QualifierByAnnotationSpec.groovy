package io.micronaut.inject.qualifiers

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import spock.lang.Specification

import java.lang.annotation.Annotation
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class QualifierByAnnotationSpec extends Specification {

    void 'test a qualifier annotation with members is compared by them'() {
        expect:
        Qualifiers.byAnnotation(chunky(true)) instanceof AnnotationMetadataQualifier
    }

    void 'test a qualifier annotation with no member qualifies by its type'() {
        expect:
        Qualifiers.byAnnotation(plain()) instanceof AnnotationQualifier
    }

    void 'test a qualifier annotation whose member cannot be read qualifies by its type'() {
        given: 'an annotation that will not answer for one of its members'
        Annotation unreadable = proxy(Chunky) { Object proxy, java.lang.reflect.Method method, Object[] args ->
            if (method.name == 'annotationType') {
                return Chunky
            }
            throw new UnsupportedOperationException("the member ${method.name} cannot be read")
        }

        expect: 'the qualifier compares no members rather than the ones it did manage to read'
        Qualifiers.byAnnotation(unreadable) instanceof AnnotationQualifier
    }

    void 'test equality between qualifiers of the same memberless annotation is symmetric'() {
        given:
        def byInstance = Qualifiers.byAnnotation(plain())
        def byValue = Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, AnnotationValue.builder(Plain).build())

        expect:
        byInstance instanceof AnnotationQualifier
        byValue instanceof AnnotationMetadataQualifier
        byInstance.equals(byValue) == byValue.equals(byInstance)
        byValue.equals(byInstance)
        byInstance.hashCode() == byValue.hashCode()
    }

    private static Chunky chunky(boolean realChunky) {
        proxy(Chunky) { Object proxy, java.lang.reflect.Method method, Object[] args ->
            switch (method.name) {
                case 'annotationType': return Chunky
                case 'realChunky': return realChunky
                case 'toString': return "@Chunky(realChunky=$realChunky)"
                case 'hashCode': return realChunky.hashCode()
                default: return false
            }
        }
    }

    private static Plain plain() {
        proxy(Plain) { Object proxy, java.lang.reflect.Method method, Object[] args ->
            switch (method.name) {
                case 'annotationType': return Plain
                case 'toString': return '@Plain'
                case 'hashCode': return 0
                default: return false
            }
        }
    }

    private static <A extends Annotation> A proxy(Class<A> type, Closure<?> handler) {
        (A) Proxy.newProxyInstance(type.classLoader, [type] as Class[], handler as InvocationHandler)
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Chunky {
        boolean realChunky()
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Plain {
    }
}

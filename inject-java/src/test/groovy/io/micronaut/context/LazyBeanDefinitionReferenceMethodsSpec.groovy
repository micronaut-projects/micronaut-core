package io.micronaut.context

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationMetadataDelegate
import io.micronaut.core.annotation.AnnotationMetadataProvider
import io.micronaut.core.beans.BeanInfo
import io.micronaut.core.order.Ordered
import io.micronaut.inject.BeanContextConditional
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.BeanType
import io.micronaut.inject.QualifiedBeanType
import spock.lang.Specification

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * A lazy reference answers each question itself or through the loaded definition: no default method of an interface
 * answers in its place, which would answer for no definition at all.
 */
class LazyBeanDefinitionReferenceMethodsSpec extends Specification {

    void "every method of the annotation metadata is declared by the descriptor metadata or delegated to the definition"() {
        given:
        Set<String> declared = signatures(DescriptorAnnotationMetadata.declaredMethods) + signatures(AnnotationMetadataDelegate.declaredMethods)

        expect:
        AnnotationMetadata.methods.findAll { !Modifier.isStatic(it.modifiers) && !declared.contains(signature(it)) }*.toString() == []
    }

    void "every method of a reference is declared by the lazy reference or delegated to the definition"() {
        given:
        Set<String> declared = signatures(LazyBeanDefinitionReference.declaredMethods) + signatures(AnnotationMetadataDelegate.declaredMethods)
        List<Class<?>> interfaces = [BeanDefinitionReference, QualifiedBeanType, BeanType, BeanInfo, BeanContextConditional, Ordered, AnnotationMetadataProvider]

        expect:
        interfaces.collectMany { Class<?> type -> type.methods.toList() }
            .findAll { !Modifier.isStatic(it.modifiers) && !declared.contains(signature(it)) }
            *.toString()
            .unique() == []
    }

    private static Set<String> signatures(Method[] methods) {
        methods.findAll { !Modifier.isStatic(it.modifiers) && !it.synthetic }.collect { signature(it) } as Set
    }

    private static String signature(Method method) {
        method.name + method.parameterTypes*.name.toString()
    }
}

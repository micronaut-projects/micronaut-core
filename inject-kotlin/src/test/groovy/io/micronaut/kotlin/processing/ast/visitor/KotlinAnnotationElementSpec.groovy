package io.micronaut.kotlin.processing.ast.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.core.annotation.Generated
import io.micronaut.core.annotation.Introspected
import io.micronaut.inject.ast.AnnotationElement
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import jakarta.inject.Singleton

import java.lang.annotation.ElementType
import java.lang.annotation.RetentionPolicy

class KotlinAnnotationElementSpec extends AbstractKotlinCompilerSpec {

    void "test isInherited"() {
        given:
        InheritedVisitor.RESULTS.clear()
        buildClassElement("test.MyBean", """
package test

@java.lang.annotation.Inherited
annotation class MyInheritedAnn

annotation class MyPlainAnn

class MyBean
""")

        expect:
        InheritedVisitor.RESULTS['test.MyInheritedAnn']
        InheritedVisitor.RESULTS['test.MyPlainAnn'] == false
        InheritedVisitor.RESULTS[Introspected.name]
        InheritedVisitor.RESULTS[Singleton.name] == false

        and: "a copy of the element is still an annotation element and keeps the answer"
        InheritedVisitor.RESULTS['test.MyInheritedAnn:copy']
        InheritedVisitor.RESULTS['test.MyPlainAnn:copy'] == false
    }

    void "test getTargets, getRepeatableContainer and getRetentionPolicy"() {
        given:
        DeclarationVisitor.TARGETS.clear()
        DeclarationVisitor.CONTAINERS.clear()
        DeclarationVisitor.RETENTIONS.clear()
        buildClassElement("test.MyBean", """
package test

import kotlin.annotation.AnnotationTarget.*

@Target(CLASS, ANNOTATION_CLASS, FUNCTION, PROPERTY_GETTER, VALUE_PARAMETER, TYPE, TYPE_PARAMETER, PROPERTY, FIELD, CONSTRUCTOR, LOCAL_VARIABLE, EXPRESSION, FILE, TYPEALIAS)
@Retention(AnnotationRetention.SOURCE)
annotation class MyTargetedAnn

@Target(FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Repeatable
annotation class MyRepeatableAnn(val value: String)

@JvmRepeatable(MyJvmRepeatableAnns::class)
annotation class MyJvmRepeatableAnn(val value: String)

annotation class MyJvmRepeatableAnns(val value: Array<MyJvmRepeatableAnn>)

@Target()
annotation class MyUntargetedAnn

annotation class MyPlainAnn

class MyBean
""")

        expect: "kotlin.annotation.AnnotationTarget is mapped to java.lang.annotation.ElementType"
        DeclarationVisitor.TARGETS['test.MyTargetedAnn'] == EnumSet.of(
            ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.PARAMETER,
            ElementType.TYPE_USE, ElementType.TYPE_PARAMETER, ElementType.FIELD, ElementType.CONSTRUCTOR,
            ElementType.LOCAL_VARIABLE
        )
        DeclarationVisitor.TARGETS['test.MyRepeatableAnn'] == EnumSet.of(ElementType.METHOD)
        DeclarationVisitor.TARGETS['test.MyUntargetedAnn'].isEmpty()
        DeclarationVisitor.TARGETS['test.MyPlainAnn'] == AnnotationElement.DEFAULT_TARGETS

        and: "a Java @Target on the classpath is read as well, through the kotlin.annotation.AnnotationTarget view KSP gives of it: PACKAGE has no Kotlin target"
        DeclarationVisitor.TARGETS[Introspected.name] == EnumSet.of(ElementType.TYPE, ElementType.ANNOTATION_TYPE)
        DeclarationVisitor.TARGETS[Singleton.name] == AnnotationElement.DEFAULT_TARGETS

        and: "kotlin.annotation.Repeatable, @JvmRepeatable and java.lang.annotation.Repeatable alike"
        DeclarationVisitor.CONTAINERS['test.MyRepeatableAnn'] == Optional.of('test.MyRepeatableAnn$Container')
        DeclarationVisitor.CONTAINERS['test.MyJvmRepeatableAnn'] == Optional.of('test.MyJvmRepeatableAnns')
        DeclarationVisitor.CONTAINERS['test.MyPlainAnn'] == Optional.empty()
        DeclarationVisitor.CONTAINERS[Property.name] == Optional.of(PropertySource.name)
        DeclarationVisitor.CONTAINERS[Singleton.name] == Optional.empty()

        and: "the retention"
        DeclarationVisitor.RETENTIONS['test.MyTargetedAnn'] == RetentionPolicy.SOURCE
        DeclarationVisitor.RETENTIONS['test.MyRepeatableAnn'] == RetentionPolicy.CLASS
        DeclarationVisitor.RETENTIONS['test.MyPlainAnn'] == RetentionPolicy.RUNTIME
        DeclarationVisitor.RETENTIONS[Generated.name] == RetentionPolicy.CLASS
        DeclarationVisitor.RETENTIONS[Singleton.name] == RetentionPolicy.RUNTIME

        and: "a copy of the element keeps the answers"
        DeclarationVisitor.TARGETS['test.MyRepeatableAnn:copy'] == EnumSet.of(ElementType.METHOD)
        DeclarationVisitor.CONTAINERS['test.MyRepeatableAnn:copy'] == Optional.of('test.MyRepeatableAnn$Container')
        DeclarationVisitor.RETENTIONS['test.MyRepeatableAnn:copy'] == RetentionPolicy.CLASS
    }

    static class InheritedVisitor implements TypeElementVisitor<Object, Object> {

        static final Map<String, Boolean> RESULTS = [:]

        @Override
        void start(VisitorContext visitorContext) {
            for (String name : ['test.MyInheritedAnn', 'test.MyPlainAnn', Introspected.name, Singleton.name]) {
                visitorContext.getClassElement(name).ifPresent { ClassElement ce ->
                    RESULTS.put(name, ((AnnotationElement) ce).isInherited())
                    def copy = ce.withAnnotationMetadata(ce.getAnnotationMetadata())
                    RESULTS.put(name + ':copy', ((AnnotationElement) copy).isInherited())
                }
            }
        }
    }

    static class DeclarationVisitor implements TypeElementVisitor<Object, Object> {

        static final Map<String, Set<ElementType>> TARGETS = [:]
        static final Map<String, Optional<String>> CONTAINERS = [:]
        static final Map<String, RetentionPolicy> RETENTIONS = [:]

        @Override
        void start(VisitorContext visitorContext) {
            def names = ['test.MyTargetedAnn', 'test.MyRepeatableAnn', 'test.MyJvmRepeatableAnn', 'test.MyUntargetedAnn', 'test.MyPlainAnn',
                         Introspected.name, Singleton.name, Property.name, Generated.name]
            for (String name : names) {
                visitorContext.getClassElement(name).ifPresent { ClassElement ce ->
                    record(name, (AnnotationElement) ce)
                    record(name + ':copy', (AnnotationElement) ce.withAnnotationMetadata(ce.getAnnotationMetadata()))
                }
            }
        }

        private static void record(String name, AnnotationElement element) {
            TARGETS.put(name, element.getTargets())
            CONTAINERS.put(name, element.getRepeatableContainer())
            RETENTIONS.put(name, element.getRetentionPolicy())
        }
    }
}

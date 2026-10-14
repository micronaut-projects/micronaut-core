package io.micronaut.kotlin.processing.ast.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.core.annotation.Generated
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.AnnotationElement
import io.micronaut.inject.ast.AnnotationMemberElement
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import jakarta.inject.Named
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

    void "test getMembers reports the members in declaration order with their types, defaults and annotations"() {
        given:
        MembersVisitor.clear()
        buildClassElement("test.MyBean", """
package test

import kotlin.annotation.AnnotationTarget.*
import kotlin.reflect.KClass
import java.lang.annotation.ElementType

@Target(PROPERTY, VALUE_PARAMETER)
annotation class NonBinding

class Outer {
    annotation class Nested(val value: String, val flag: Boolean = true)
}

annotation class Marker

@Marker
annotation class MyAnn(
    val name: String,
    @NonBinding val description: String = "",
    val priority: Int = 5,
    val type: KClass<*> = Any::class,
    val target: ElementType = ElementType.METHOD,
    val tags: Array<String> = ["a", "b"],
    val nested: Outer.Nested = Outer.Nested("n"),
    val list: Array<Outer.Nested> = []
)

annotation class UnusedAnn(val value: String = "unused")

@MyAnn(name = "bean")
@Outer.Nested("x")
@jakarta.inject.Named("bean")
@io.micronaut.context.annotation.Property(name = "x")
class MyBean
""")
        def members = MembersVisitor.MEMBERS['test.MyAnn']

        expect: "the members in declaration order, each a member of the annotation"
        MembersVisitor.NAMES['test.MyAnn'] == ['name', 'description', 'priority', 'type', 'target', 'tags', 'nested', 'list']
        MembersVisitor.DECLARING['test.MyAnn'] == ['test.MyAnn'] as Set

        and: "the declared types, an array by its component"
        MembersVisitor.TYPES['test.MyAnn'] == [
            name: 'java.lang.String', description: 'java.lang.String', priority: 'int', type: 'kotlin.reflect.KClass',
            target: ElementType.name, tags: 'java.lang.String', nested: 'test.Outer$Nested', list: 'test.Outer$Nested'
        ]
        MembersVisitor.ARRAYS['test.MyAnn'] == ['tags', 'list']

        and: "the defaults, as the metadata records them"
        members['name'] == Optional.empty()
        members['description'] == Optional.of('')
        members['priority'] == Optional.of(5)
        members['type'].get() instanceof AnnotationClassValue
        ((AnnotationClassValue) members['type'].get()).getName() == 'java.lang.Object'
        members['target'] == Optional.of('METHOD')
        members['tags'].get() == ['a', 'b'] as String[]
        members['nested'].get() instanceof AnnotationValue
        ((AnnotationValue) members['nested'].get()).getAnnotationName() == 'test.Outer$Nested'
        ((AnnotationValue) members['nested'].get()).stringValue().get() == 'n'

        and: "the annotations of a member are its own, not those of the annotation class"
        MembersVisitor.NON_BINDING['test.MyAnn'] == ['description']
        MembersVisitor.MARKED['test.MyAnn'] == ['name']

        and: "the type of a nested annotation member is an annotation element, through an array too, and the nested annotation has members of its own"
        MembersVisitor.ANNOTATION_TYPED['test.MyAnn'] == ['nested', 'list']
        MembersVisitor.ARRAY_COMPONENT_MEMBERS['test.MyAnn'] == [list: ['value', 'flag']]
        members['list'].get() instanceof Object[]
        ((Object[]) members['list'].get()).length == 0
        MembersVisitor.NAMES['test.Outer$Nested'] == ['value', 'flag']
        MembersVisitor.MEMBERS['test.Outer$Nested'] == [value: Optional.empty(), flag: Optional.of(true)]

        and: "KSP only shows the defaults of an annotation on a use of it, so an annotation never used reports none"
        MembersVisitor.NAMES['test.UnusedAnn'] == ['value']
        MembersVisitor.MEMBERS['test.UnusedAnn'] == [value: Optional.empty()]

        and: "a Java annotation on the classpath answers its members as properties too"
        MembersVisitor.NAMES[Named.name] == ['value']
        MembersVisitor.MEMBERS[Named.name] == [value: Optional.of('')]
        MembersVisitor.TYPES[Named.name] == [value: 'java.lang.String']
        MembersVisitor.NAMES[Singleton.name] == []
        MembersVisitor.NAMES[Property.name] == ['name', 'value', 'defaultValue']
        MembersVisitor.MEMBERS[Property.name] == [name: Optional.empty(), value: Optional.of(''), defaultValue: Optional.of('')]

        and: "a copy of the element keeps the members"
        MembersVisitor.NAMES['test.MyAnn:copy'] == MembersVisitor.NAMES['test.MyAnn']
    }

    static class MembersVisitor implements TypeElementVisitor<Object, Object> {

        static final Map<String, List<String>> NAMES = [:]
        static final Map<String, Map<String, Optional<Object>>> MEMBERS = [:]
        static final Map<String, Map<String, String>> TYPES = [:]
        static final Map<String, Set<String>> DECLARING = [:]
        static final Map<String, List<String>> NON_BINDING = [:]
        static final Map<String, List<String>> ANNOTATION_TYPED = [:]
        static final Map<String, List<String>> ARRAYS = [:]
        static final Map<String, List<String>> MARKED = [:]
        static final Map<String, Map<String, List<String>>> ARRAY_COMPONENT_MEMBERS = [:]

        static void clear() {
            [NAMES, MEMBERS, TYPES, DECLARING, NON_BINDING, ANNOTATION_TYPED, ARRAYS, MARKED, ARRAY_COMPONENT_MEMBERS]*.clear()
        }

        @Override
        void start(VisitorContext visitorContext) {
            def names = ['test.MyAnn', 'test.Outer$Nested', 'test.UnusedAnn', Named.name, Singleton.name, Property.name]
            for (String name : names) {
                visitorContext.getClassElement(name).ifPresent { ClassElement ce ->
                    record(name, (AnnotationElement) ce)
                    record(name + ':copy', (AnnotationElement) ce.withAnnotationMetadata(ce.getAnnotationMetadata()))
                }
            }
        }

        private static void record(String name, AnnotationElement element) {
            List<? extends AnnotationMemberElement> members = element.getMembers()
            NAMES.put(name, members*.getName())
            MEMBERS.put(name, members.collectEntries { [(it.getName()): it.getDefaultValue()] })
            TYPES.put(name, members.collectEntries { [(it.getName()): it.getType().getName()] })
            DECLARING.put(name, members*.getDeclaringType()*.getName() as Set)
            NON_BINDING.put(name, members.findAll { it.hasAnnotation('test.NonBinding') }*.getName())
            ANNOTATION_TYPED.put(name, members.findAll { it.getType() instanceof AnnotationElement }*.getName())
            ARRAYS.put(name, members.findAll { it.getType().isArray() }*.getName())
            // the annotation class carries the marker; the first member is given it, which the others must not see
            if (!members.isEmpty() && element.hasAnnotation('test.Marker')) {
                members[0].annotate('test.Marker')
            }
            MARKED.put(name, members.findAll { it.hasAnnotation('test.Marker') }*.getName())
            ARRAY_COMPONENT_MEMBERS.put(name, members
                .findAll { it.getType().isArray() && it.getType().fromArray() instanceof AnnotationElement }
                .collectEntries { [(it.getName()): ((AnnotationElement) it.getType().fromArray()).getMembers()*.getName()] })
        }
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

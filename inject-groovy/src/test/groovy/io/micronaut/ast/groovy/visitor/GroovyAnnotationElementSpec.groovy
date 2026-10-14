package io.micronaut.ast.groovy.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.core.annotation.Generated
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.AnnotationElement
import io.micronaut.inject.ast.AnnotationMemberElement
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.ErrorCollector
import org.codehaus.groovy.control.SourceUnit

import java.lang.annotation.ElementType
import java.lang.annotation.RetentionPolicy

class GroovyAnnotationElementSpec extends AbstractBeanDefinitionSpec {

    void "test isInherited for an annotation declared as @Inherited"() {
        given:
        def element = buildClassElement("""
package test

import java.lang.annotation.Inherited

@Inherited
@interface MyAnn {
}
""")
        expect:
        element instanceof AnnotationElement
        ((AnnotationElement) element).isInherited()
    }

    void "test isInherited for an annotation not declared as @Inherited"() {
        given:
        def element = buildClassElement("""
package test

@interface MyAnn {
}
""")
        expect:
        element instanceof AnnotationElement
        !((AnnotationElement) element).isInherited()
    }

    void "test isInherited survives a copy of the element"() {
        given:
        def element = (AnnotationElement) buildClassElement("""
package test

import java.lang.annotation.Inherited

@Inherited
@interface MyAnn {
}
""")
        def copy = element.withAnnotationMetadata(element.getAnnotationMetadata())

        expect:
        copy instanceof AnnotationElement
        ((AnnotationElement) copy).isInherited()
    }

    void "test isInherited for annotations on the classpath"() {
        given:
        def visitorContext = newVisitorContext()
        def introspected = visitorContext.getClassElement(Introspected.name).get()
        def singleton = visitorContext.getClassElement(Singleton.name).get()

        expect:
        introspected instanceof AnnotationElement
        ((AnnotationElement) introspected).isInherited()
        singleton instanceof AnnotationElement
        !((AnnotationElement) singleton).isInherited()
    }

    void "test isInherited for annotations resolved from a class"() {
        given:
        def visitorContext = newVisitorContext()
        def introspected = visitorContext.getClassElement(Introspected).get()
        def singleton = visitorContext.getClassElement(Singleton).get()

        expect:
        ((AnnotationElement) introspected).isInherited()
        !((AnnotationElement) singleton).isInherited()
    }

    void "test getTargets, getRepeatableContainer and getRetentionPolicy as declared in the source"() {
        given:
        def element = (AnnotationElement) buildClassElement("test.MyAnn", """
package test

import java.lang.annotation.*

@Target([ElementType.TYPE, ElementType.METHOD, ElementType.TYPE_USE])
@Retention(RetentionPolicy.SOURCE)
@Repeatable(MyAnns)
@interface MyAnn {
}

@interface MyAnns {
    MyAnn[] value()
}
""")
        expect:
        element.getTargets() == EnumSet.of(ElementType.TYPE, ElementType.METHOD, ElementType.TYPE_USE)
        element.getRepeatableContainer().get() == 'test.MyAnns'
        element.getRetentionPolicy() == RetentionPolicy.SOURCE

        and: "a copy of the element keeps the answers"
        def copy = (AnnotationElement) element.withAnnotationMetadata(element.getAnnotationMetadata())
        copy.getTargets() == element.getTargets()
        copy.getRepeatableContainer().get() == 'test.MyAnns'
        copy.getRetentionPolicy() == RetentionPolicy.SOURCE
    }

    void "test a single @Target value"() {
        given:
        def element = (AnnotationElement) buildClassElement("""
package test

import java.lang.annotation.*

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
@interface MyAnn {
}
""")
        expect:
        element.getTargets() == EnumSet.of(ElementType.FIELD)
        !element.getRepeatableContainer().isPresent()
        element.getRetentionPolicy() == RetentionPolicy.CLASS
    }

    void "test the JLS defaults when the annotation declares no @Target, @Repeatable or @Retention"() {
        given:
        def element = (AnnotationElement) buildClassElement("""
package test

@interface MyAnn {
}
""")
        expect:
        element.getTargets() == AnnotationElement.DEFAULT_TARGETS
        !element.getRepeatableContainer().isPresent()
        element.getRetentionPolicy() == RetentionPolicy.RUNTIME
    }

    void "test an empty @Target means the annotation targets nothing"() {
        given:
        def element = (AnnotationElement) buildClassElement("""
package test

import java.lang.annotation.*

@Target([])
@interface MyAnn {
}
""")
        expect:
        element.getTargets().isEmpty()
    }

    void "test getTargets, getRepeatableContainer and getRetentionPolicy for annotations on the classpath"() {
        given:
        def visitorContext = newVisitorContext()
        def introspected = (AnnotationElement) visitorContext.getClassElement(Introspected.name).get()
        def singleton = (AnnotationElement) visitorContext.getClassElement(Singleton.name).get()
        def property = (AnnotationElement) visitorContext.getClassElement(Property.name).get()
        def generated = (AnnotationElement) visitorContext.getClassElement(Generated.name).get()

        expect:
        introspected.getTargets() == EnumSet.of(ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.PACKAGE)
        singleton.getTargets() == AnnotationElement.DEFAULT_TARGETS
        property.getRepeatableContainer() == Optional.of(PropertySource.name)
        singleton.getRepeatableContainer() == Optional.empty()
        generated.getRetentionPolicy() == RetentionPolicy.CLASS
        singleton.getRetentionPolicy() == RetentionPolicy.RUNTIME
    }

    void "test getMembers reports the members in declaration order with their types, defaults and annotations"() {
        given:
        def element = (AnnotationElement) buildClassElement("""
package test

import java.lang.annotation.*

@Target(ElementType.METHOD)
@interface NonBinding {
}

class Outer {
    @interface Nested {
        String value()
        boolean flag() default true
    }
}

@interface Marker {
}

@Marker
@interface MyAnn {
    String name()
    @NonBinding String description() default ""
    int priority() default 5
    Class<?> type() default Object.class
    ElementType target() default ElementType.METHOD
    String[] tags() default ["a", "b"]
    Outer.Nested nested() default @Outer.Nested(value = "n")
    Outer.Nested[] list() default []
}
""")
        def members = element.getMembers()

        expect: "the members in declaration order, each a member of the annotation"
        element.getName() == 'test.MyAnn'
        members*.getName() == ['name', 'description', 'priority', 'type', 'target', 'tags', 'nested', 'list']
        members.every { it instanceof AnnotationMemberElement && it.getDeclaringType().getName() == 'test.MyAnn' }

        and: "the declared types"
        members[0].getType().getName() == 'java.lang.String'
        members[2].getType().getName() == 'int'
        members[3].getType().getName() == 'java.lang.Class'
        members[4].getType().getName() == ElementType.name
        members[5].getType().isArray()
        members[5].getType().fromArray().getName() == 'java.lang.String'
        members[6].getType().getName() == 'test.Outer$Nested'

        and: "the defaults, as the metadata records them"
        members[0].getDefaultValue() == Optional.empty()
        members[1].getDefaultValue() == Optional.of('')
        members[2].getDefaultValue() == Optional.of(5)
        members[3].getDefaultValue().get() instanceof AnnotationClassValue
        ((AnnotationClassValue) members[3].getDefaultValue().get()).getName() == 'java.lang.Object'
        members[4].getDefaultValue() == Optional.of('METHOD')
        members[5].getDefaultValue().get() == ['a', 'b'] as String[]
        members[6].getDefaultValue().get() instanceof AnnotationValue
        ((AnnotationValue) members[6].getDefaultValue().get()).getAnnotationName() == 'test.Outer$Nested'
        ((AnnotationValue) members[6].getDefaultValue().get()).stringValue().get() == 'n'

        and: "the annotations of a member are its own, not those of the annotation interface"
        members[1].hasAnnotation('test.NonBinding')
        members.count { it.hasAnnotation('test.NonBinding') } == 1
        element.hasAnnotation('test.Marker')
        members.every { !it.hasAnnotation('test.Marker') }

        and: "a member can be annotated"
        members[0].annotate('test.Marker')
        members[0].hasAnnotation('test.Marker')
        !members[1].hasAnnotation('test.Marker')

        and: "the type of a nested annotation member is an annotation element with members of its own"
        members[6].getType() instanceof AnnotationElement
        def nested = ((AnnotationElement) members[6].getType()).getMembers()
        nested*.getName() == ['value', 'flag']
        nested[0].getDefaultValue() == Optional.empty()
        nested[1].getDefaultValue() == Optional.of(true)

        and: "an array of a nested annotation keeps the annotation element through its component"
        members[7].getType().isArray()
        members[7].getType() instanceof AnnotationElement
        members[7].getType().fromArray() instanceof AnnotationElement
        ((AnnotationElement) members[7].getType().fromArray()).getMembers()*.getName() == ['value', 'flag']
        members[6].getType().toArray() instanceof AnnotationElement
        members[6].getType().toArray().isArray()
        members[7].getDefaultValue().get() instanceof Object[]
        ((Object[]) members[7].getDefaultValue().get()).length == 0

        and: "a copy of the element keeps the members, and a copy of a member is a member"
        def copy = (AnnotationElement) element.withAnnotationMetadata(element.getAnnotationMetadata())
        copy.getMembers()*.getName() == members*.getName()
        AnnotationMemberElement memberCopy = members[1].withAnnotationMetadata(members[1].getAnnotationMetadata())
        memberCopy.getName() == 'description'
        memberCopy.getDefaultValue() == Optional.of('')
        memberCopy.hasAnnotation('test.NonBinding')
    }

    void "test getMembers for annotations on the classpath"() {
        given:
        def visitorContext = newVisitorContext()
        def named = (AnnotationElement) visitorContext.getClassElement(Named.name).get()
        def singleton = (AnnotationElement) visitorContext.getClassElement(Singleton.name).get()
        def property = (AnnotationElement) visitorContext.getClassElement(Property.name).get()

        expect:
        named.getMembers()*.getName() == ['value']
        named.getMembers()[0].getType().getName() == 'java.lang.String'
        named.getMembers()[0].getDefaultValue() == Optional.of('')
        singleton.getMembers().isEmpty()
        property.getMembers()*.getName() == ['name', 'value', 'defaultValue']
        property.getMembers()*.getDefaultValue() == [Optional.empty(), Optional.of(''), Optional.of('')]
        property.getMembers()*.getType()*.getName() == ['java.lang.String'] * 3
    }

    private static GroovyVisitorContext newVisitorContext() {
        def cc = new CompilerConfiguration()
        def sourceUnit = new SourceUnit("test", "", cc, new GroovyClassLoader(), new ErrorCollector(cc))
        return new GroovyVisitorContext(sourceUnit, new CompilationUnit())
    }
}

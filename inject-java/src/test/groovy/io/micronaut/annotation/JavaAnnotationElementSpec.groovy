package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
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

class JavaAnnotationElementSpec extends AbstractTypeElementSpec {

    void "test isInherited for an annotation declared as @Inherited"() {
        given:
        def element = buildClassElement("""
package test;

import java.lang.annotation.Inherited;

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
package test;

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
package test;

import java.lang.annotation.Inherited;

@Inherited
@interface MyAnn {
}
""")
        def copy = element.withAnnotationMetadata(element.getAnnotationMetadata())

        expect:
        copy instanceof AnnotationElement
        ((AnnotationElement) copy).isInherited()
    }

    void "test the default isInherited implementation reports false"() {
        expect: "an implementation with no native element to inspect cannot answer the question"
        !new NoNativeTypeAnnotationElement().isInherited()
    }

    void "test getTargets, getRepeatableContainer and getRetentionPolicy as declared in the source"() {
        given:
        def element = (AnnotationElement) buildClassElement("""
package test;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.SOURCE)
@Repeatable(MyAnns.class)
@interface MyAnn {
}

@Target({ElementType.TYPE, ElementType.METHOD, ElementType.TYPE_USE})
@interface MyAnns {
    MyAnn[] value();
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

    void "test a single @Target value and a nested container"() {
        given:
        def element = (AnnotationElement) buildClassElement("""
package test;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
@Repeatable(Outer.MyAnns.class)
@interface MyAnn {
}

class Outer {
    @Target(ElementType.FIELD)
    @interface MyAnns {
        MyAnn[] value();
    }
}
""")
        expect:
        element.getTargets() == EnumSet.of(ElementType.FIELD)
        element.getRepeatableContainer().get() == 'test.Outer$MyAnns'
        element.getRetentionPolicy() == RetentionPolicy.CLASS
    }

    void "test the JLS defaults when the annotation declares no @Target, @Repeatable or @Retention"() {
        given:
        def element = (AnnotationElement) buildClassElement("""
package test;

@interface MyAnn {
}
""")
        expect:
        element.getTargets() == AnnotationElement.DEFAULT_TARGETS
        !element.getTargets().contains(ElementType.TYPE_USE)
        !element.getTargets().contains(ElementType.TYPE_PARAMETER)
        element.getTargets().contains(ElementType.TYPE)
        element.getTargets().contains(ElementType.RECORD_COMPONENT)
        !element.getRepeatableContainer().isPresent()
        element.getRetentionPolicy() == RetentionPolicy.RUNTIME
    }

    void "test an empty @Target means the annotation targets nothing"() {
        given:
        def element = (AnnotationElement) buildClassElement("""
package test;

import java.lang.annotation.*;

@Target({})
@interface MyAnn {
}
""")
        expect:
        element.getTargets().isEmpty()
    }

    void "test the default implementations for an element without a native type"() {
        given:
        def element = new NoNativeTypeAnnotationElement()

        expect:
        element.getTargets() == AnnotationElement.DEFAULT_TARGETS
        !element.getRepeatableContainer().isPresent()
        element.getRetentionPolicy() == RetentionPolicy.RUNTIME
    }

    void "test getTargets, getRepeatableContainer and getRetentionPolicy for annotations on the classpath"() {
        given:
        InheritedVisitor.RESULTS.clear()
        buildBeanDefinition('test.MyBean', '''
package test;

@jakarta.inject.Singleton
class MyBean {
}
''')

        expect:
        InheritedVisitor.TARGETS[Introspected.name] == EnumSet.of(ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.PACKAGE)
        InheritedVisitor.TARGETS[Singleton.name] == AnnotationElement.DEFAULT_TARGETS
        InheritedVisitor.TARGETS[Property.name] == AnnotationElement.DEFAULT_TARGETS
        InheritedVisitor.TARGETS[Generated.name] == EnumSet.of(ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE)
        InheritedVisitor.CONTAINERS[Property.name] == Optional.of(PropertySource.name)
        InheritedVisitor.CONTAINERS[Singleton.name] == Optional.empty()
        InheritedVisitor.RETENTIONS[Generated.name] == RetentionPolicy.CLASS
        InheritedVisitor.RETENTIONS[Singleton.name] == RetentionPolicy.RUNTIME
    }

    void "test isInherited for annotations on the classpath"() {
        given:
        InheritedVisitor.RESULTS.clear()
        buildBeanDefinition('test.MyBean', '''
package test;

@jakarta.inject.Singleton
class MyBean {
}
''')

        expect:
        InheritedVisitor.RESULTS[Introspected.name]
        InheritedVisitor.RESULTS[Singleton.name] == false
    }

    void "test getMembers reports the members in declaration order with their types, defaults and annotations"() {
        given:
        def element = (AnnotationElement) buildClassElement("""
package test;

import java.lang.annotation.*;

@Marker
@interface MyAnn {
    String name();
    @NonBinding String description() default "";
    int priority() default 5;
    Class<?> type() default Object.class;
    ElementType target() default ElementType.METHOD;
    String[] tags() default {"a", "b"};
    /**
     * The nested member.
     * @return the nested annotation
     */
    Outer.Nested nested() default @Outer.Nested("n");
    Outer.Nested[] list() default {};
}

@interface Marker {
}

@Target(ElementType.METHOD)
@interface NonBinding {
}

class Outer {
    @interface Nested {
        String value();
        boolean flag() default true;
    }
}
""")
        def members = element.getMembers()

        expect: "the members in declaration order, each a member of the annotation"
        members*.getName() == ['name', 'description', 'priority', 'type', 'target', 'tags', 'nested', 'list']
        members.every { it instanceof AnnotationMemberElement && it.getDeclaringType().getName() == 'test.MyAnn' }

        and: "the declared types"
        members[0].getType().getName() == 'java.lang.String'
        members[2].getType().getName() == 'int'
        members[3].getType().getName() == 'java.lang.Class'
        members[2].isPrimitive()
        !members[0].isPrimitive()
        members[5].isArray()
        members[5].getArrayDimensions() == 1
        !members[0].isArray()
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
        members[6].getType().getDocumentation(true) == Optional.of('the nested annotation')
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
        InheritedVisitor.MEMBERS.clear()
        buildBeanDefinition('test.MyBean', '''
package test;

@jakarta.inject.Singleton
class MyBean {
}
''')

        expect:
        InheritedVisitor.MEMBERS[Named.name] == [value: Optional.of('')]
        InheritedVisitor.MEMBERS[Singleton.name] == [:]
        InheritedVisitor.MEMBERS[Property.name] == [name: Optional.empty(), value: Optional.of(''), defaultValue: Optional.of('')]
        InheritedVisitor.MEMBER_TYPES[Property.name] == [name: 'java.lang.String', value: 'java.lang.String', defaultValue: 'java.lang.String']
    }

    void "test the default getMembers implementation reports no members"() {
        expect:
        new NoNativeTypeAnnotationElement().getMembers().isEmpty()
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new InheritedVisitor()]
    }

    /**
     * An {@link AnnotationElement} that doesn't override {@code isInherited()}, to pin the behaviour
     * of the interface default.
     */
    static class NoNativeTypeAnnotationElement implements AnnotationElement {

        @Override
        String getName() {
            return 'test.MyAnn'
        }

        @Override
        boolean isProtected() {
            return false
        }

        @Override
        boolean isPublic() {
            return true
        }

        @Override
        Object getNativeType() {
            return this
        }

        @Override
        boolean isAssignable(String type) {
            return false
        }

        @Override
        ClassElement toArray() {
            throw new UnsupportedOperationException()
        }

        @Override
        ClassElement fromArray() {
            throw new UnsupportedOperationException()
        }
    }

    static class InheritedVisitor implements TypeElementVisitor<Object, Object> {

        static final Map<String, Boolean> RESULTS = [:]
        static final Map<String, Set<ElementType>> TARGETS = [:]
        static final Map<String, Optional<String>> CONTAINERS = [:]
        static final Map<String, RetentionPolicy> RETENTIONS = [:]
        static final Map<String, Map<String, Optional<Object>>> MEMBERS = [:]
        static final Map<String, Map<String, String>> MEMBER_TYPES = [:]

        @Override
        void start(VisitorContext visitorContext) {
            for (String name : [Introspected.name, Singleton.name, Property.name, Generated.name, Named.name]) {
                visitorContext.getClassElement(name).ifPresent { ClassElement ce ->
                    def annotationElement = (AnnotationElement) ce
                    RESULTS.put(name, annotationElement.isInherited())
                    TARGETS.put(name, annotationElement.getTargets())
                    CONTAINERS.put(name, annotationElement.getRepeatableContainer())
                    RETENTIONS.put(name, annotationElement.getRetentionPolicy())
                    def members = new LinkedHashMap<String, Optional<Object>>()
                    def types = new LinkedHashMap<String, String>()
                    for (AnnotationMemberElement member : annotationElement.getMembers()) {
                        members.put(member.getName(), member.getDefaultValue())
                        types.put(member.getName(), member.getType().getName())
                    }
                    MEMBERS.put(name, members)
                    MEMBER_TYPES.put(name, types)
                }
            }
        }
    }
}

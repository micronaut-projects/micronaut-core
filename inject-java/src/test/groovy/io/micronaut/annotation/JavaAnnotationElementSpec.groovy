package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
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

        @Override
        void start(VisitorContext visitorContext) {
            for (String name : [Introspected.name, Singleton.name, Property.name, Generated.name]) {
                visitorContext.getClassElement(name).ifPresent { ClassElement ce ->
                    def annotationElement = (AnnotationElement) ce
                    RESULTS.put(name, annotationElement.isInherited())
                    TARGETS.put(name, annotationElement.getTargets())
                    CONTAINERS.put(name, annotationElement.getRepeatableContainer())
                    RETENTIONS.put(name, annotationElement.getRetentionPolicy())
                }
            }
        }
    }
}

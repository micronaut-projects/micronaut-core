package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.Introspected
import io.micronaut.inject.ast.AnnotationElement
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import jakarta.inject.Singleton

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

        @Override
        void start(VisitorContext visitorContext) {
            for (String name : [Introspected.name, Singleton.name]) {
                visitorContext.getClassElement(name).ifPresent { ClassElement ce ->
                    RESULTS.put(name, ((AnnotationElement) ce).isInherited())
                }
            }
        }
    }
}

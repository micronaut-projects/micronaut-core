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
        !InheritedVisitor.RESULTS[Singleton.name]
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new InheritedVisitor()]
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

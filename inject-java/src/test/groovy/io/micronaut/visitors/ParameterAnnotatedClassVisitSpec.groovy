package io.micronaut.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * A class whose only annotations of interest are on constructor or method parameters must reach
 * a visitor of those annotations, whether it is a top-level or a nested class.
 */
class ParameterAnnotatedClassVisitSpec extends AbstractTypeElementSpec {

    void 'classes annotated only on constructor or method parameters are visited once'() {
        given:
        ParamVisitor.visited.clear()

        when:
        buildClassLoader('test.Top', '''
package test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@interface ParamMarker {}

class Top {
    public Top(@ParamMarker String a) {
    }

    public static class NestedCtor {
        public NestedCtor(@ParamMarker String a) {
        }
    }

    public static class NestedMethod {
        public void m(@ParamMarker String a) {
        }
    }

    public static class NotAnnotated {
        public NotAnnotated(String a) {
        }
    }
}
''')

        then:
        ParamVisitor.visited.count('test.Top') == 1
        ParamVisitor.visited.count('test.Top$NestedCtor') == 1
        ParamVisitor.visited.count('test.Top$NestedMethod') == 1
        !ParamVisitor.visited.contains('test.Top$NotAnnotated')
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new ParamVisitor()]
    }

    static class ParamVisitor implements TypeElementVisitor<Object, Object> {
        static List<String> visited = []

        @Override
        Set<String> getSupportedAnnotationNames() {
            return ['test.ParamMarker'] as Set
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            visited << element.name
        }
    }
}

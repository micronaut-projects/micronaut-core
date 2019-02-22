package io.micronaut.inject.visitor

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement

import javax.annotation.processing.SupportedAnnotationTypes

class ElementAnnotateSpec extends AbstractTypeElementSpec {

    void "test that elements can be dynamically annotated at compilation time"() {
        given:
        def definition = buildBeanDefinition('test.TestListener', '''
package test;
import io.micronaut.context.annotation.*;
import javax.inject.Singleton;

@Singleton
class TestListener {

    @Executable
    void receive(String v) {
    }
    
    @Executable
    int[] receiveArray(int[] v) {
        return v;
    }
    
    @Executable
    int receiveInt(int v) {
        return v;
    }
}

''')

        expect:
        definition.hasAnnotation("foo.bar.Ann")
        definition.getValue("foo.bar.Ann", "foo", String).get() == 'bar'
        definition.findMethod("receive", String).get().hasAnnotation('foo.bar.Ann')
    }

    @Override
    protected JavaParser newJavaParser() {
        return new JavaParser() {
            @Override
            protected TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
                return new MyTypeElementVisitorProcessor()
            }
        }
    }

    @SupportedAnnotationTypes("*")
    static class MyTypeElementVisitorProcessor extends TypeElementVisitorProcessor {
        @Override
        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
            return [new MyAnnotatingTypeElementVisitor()]
        }
    }

    static class MyAnnotatingTypeElementVisitor implements TypeElementVisitor {
        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            element.annotate("foo.bar.Ann") { AnnotationValueBuilder builder ->
                builder.member("foo", "bar")
            }
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            element.annotate("foo.bar.Ann") { AnnotationValueBuilder builder ->
                builder.member("foo", "bar")
            }
        }
    }
}

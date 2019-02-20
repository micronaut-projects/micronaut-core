package io.micronaut.inject.visitor

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.groovy.TypeElementVisitorTransform
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement

class ElementAnnotateSpec extends AbstractBeanDefinitionSpec{

    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, MyAnnotatingTypeElementVisitor.name)
    }

    def cleanup() {
        AllElementsVisitor.clearVisited()
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, "")
    }

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
}

''')

        expect:
        definition.hasAnnotation("foo.bar.Ann")
        definition.getValue("foo.bar.Ann", "foo", String).get() == 'bar'
        definition.findMethod("receive", String).get().hasAnnotation('foo.bar.Ann')
    }

    static class MyAnnotatingTypeElementVisitor implements TypeElementVisitor {
        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == 'test.TestListener') {
                element.annotate("foo.bar.Ann") { AnnotationValueBuilder builder ->
                    builder.member("foo", "bar")
                }
            }
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            if (element.declaringType.name == 'test.TestListener') {
                element.annotate("foo.bar.Ann") { AnnotationValueBuilder builder ->
                    builder.member("foo", "bar")
                }
            }
        }
    }
}

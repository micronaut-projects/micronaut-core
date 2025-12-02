package io.micronaut.python.annotation.processing.test.annotate

import io.micronaut.context.annotation.Prototype
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class AnnotateClassSpec extends AbstractPythonTypeElementSpec {

    void 'test annotating'() {
        when:
            def definition = buildBeanDefinition('python', 'AnnotateClass', '''

from micronaut.context.annotation import Executable
from jakarta.inject import Singleton

class AnnotateClass:

    @Executable
    def myMethod1(self):
        return "null"

''')
        then:
            definition.hasAnnotation(MyAnnotation)
    }

    static class AnnotateClassVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.getSimpleName().endsWith("AnnotateClass")) {
                element.annotate(MyAnnotation)
                element.annotate(Prototype)

                // Validate the cache is working

                def newClassElement = context.getClassElement(element.name).get()
                assert newClassElement.hasAnnotation(MyAnnotation)
                assert newClassElement.hasAnnotation(Prototype)
            }
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }

}

package io.micronaut.python.annotation.processing.test.annotate

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Executable
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class AnnotateMethodSpec extends AbstractPythonTypeElementSpec {

    void 'test annotating'() {
        when:
            def definition = buildBeanDefinition('python', 'AnnotationMethodClass', '''
from micronaut.context.annotation import Executable
from micronaut.context.annotation import Bean

class MyBean1:
    name: str

@Bean
class AnnotationMethodClass:

    @Executable
    def myMethod1(self) -> MyBean1:
        return nil

    @Executable
    def myMethod2(self,) -> MyBean1:
        return nil

''')
        then: "myMethod1 has added annotation on the method and it's seen on the return type"
            definition.getRequiredMethod("myMethod1").hasAnnotation(MyAnnotation)
            definition.getRequiredMethod("myMethod1").getReturnType().getAnnotationMetadata().hasAnnotation(MyAnnotation)
            definition.getRequiredMethod("myMethod1").getReturnType().asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation)

        and: "myMethod2 doesn't have the same annotation on the same type"
            !definition.getRequiredMethod("myMethod2").hasAnnotation(MyAnnotation)
            !definition.getRequiredMethod("myMethod2").getReturnType().getAnnotationMetadata().hasAnnotation(MyAnnotation)
            !definition.getRequiredMethod("myMethod2").getReturnType().asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation)
    }

    static class AnnotationMethodVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.getClass().getName().containsIgnoreCase("java")) {
                return
            }
            if (element.getSimpleName() == "AnnotationMethodClass") {
                def myMethod1 = element.findMethod("myMethod1").get()
                assert myMethod1.getAnnotationMetadata().getAnnotationNames().asList() == [Executable.class.name, Bean.class.name]
                myMethod1.annotate(MyAnnotation)
                assert myMethod1.getAnnotationMetadata().getAnnotationNames().asList() == [Executable.class.name, Bean.class.name, MyAnnotation.class.name]
                assert myMethod1.getAnnotationMetadata().getAnnotationNames().asList() == [Executable.class.name, Bean.class.name, MyAnnotation.class.name]
                assert myMethod1.getReturnType().getAnnotationNames().isEmpty()
                assert myMethod1.getReturnType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty()
                assert myMethod1.getReturnType().getType().getAnnotationMetadata().isEmpty()

                // Validate the cache is working
                assert context.getClassElement("python.AnnotationMethodClass").get()
                        .findMethod("myMethod1").get()
                        .getAnnotationMetadata().getAnnotationNames().asList() == [Executable.class.name, Bean.class.name, MyAnnotation.class.name]

                // Test the second method with the same type doesn't have the annotations

                def myMethod2 = element.findMethod("myMethod2").get()
                assert myMethod2.getAnnotationMetadata().getAnnotationNames().asList() == [Executable.class.name, Bean.class.name]
                assert myMethod2.getReturnType().getAnnotationNames().isEmpty()
                assert myMethod2.getReturnType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty()

                // Validate the cache is working
                assert context.getClassElement("python.AnnotationMethodClass").get()
                        .findMethod("myMethod2").get()
                        .getAnnotationMetadata().getAnnotationNames().asList() == [Executable.class.name, Bean.class.name]

                assert context.getClassElement("python.MyBean1").get().getAnnotationMetadata().isEmpty()
            }
        }
    }

}

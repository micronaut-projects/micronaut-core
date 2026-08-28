package io.micronaut.python.annotation.processing.test.annotate

import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class AnnotatePropertySpec extends AbstractPythonTypeElementSpec {

    void 'test annotating'() {
        when:
            def definition = buildBeanDefinition('python', 'AnnotationPropertyClass', '''
from micronaut.context.annotation import Bean
from jakarta.inject import Inject
from typing import Annotated

@Bean
class AnnotationPropertyClass:

    myProp1: Annotated[MyBean1, Inject] = None

    myProp2: Annotated[MyBean1, Inject] = None

class MyBean1:
    name: str
''')
        then:
            def method1 = definition.getInjectedMethods()[0]
            method1.getAnnotationMetadata().hasAnnotation(MyAnnotation)
        and:
            def method2 = definition.getInjectedMethods()[1]
            !method2.getAnnotationMetadata().hasAnnotation(MyAnnotation)
    }

    static class AnnotationFieldVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.getClass().getName().containsIgnoreCase("java")) {
                return
            }
            if (element.getSimpleName() == "AnnotationPropertyClass") {
                def myProperty1 = element.getBeanProperties().stream().filter {p -> (p.name == "myProp1") }.findFirst().get()
                assert myProperty1.getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT]
                myProperty1.annotate(MyAnnotation)
                assert myProperty1.getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT, MyAnnotation.class.name]
                assert myProperty1.getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT, MyAnnotation.class.name]
                assert myProperty1.getType().getAnnotationNames().isEmpty()
                assert myProperty1.getType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty()
                assert myProperty1.getType().getType().getAnnotationMetadata().isEmpty()
                assert myProperty1.getGenericType().getAnnotationNames().isEmpty()
                assert myProperty1.getGenericType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty()
                assert myProperty1.getGenericType().getType().getAnnotationMetadata().isEmpty()

                // Validate the cache is working
                assert context.getClassElement("python.AnnotationPropertyClass").get()
                        .getBeanProperties().stream().filter {p -> (p.name == "myProp1") }.findFirst().get()
                        .getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT, MyAnnotation.class.name]

                // Test the second method with the same type doesn't have the annotations

                def myProperty2 = element.getBeanProperties().stream().filter {p -> (p.name == "myProp2") }.findFirst().get()
                assert myProperty2.getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT]
                assert myProperty2.getType().getAnnotationNames().isEmpty()
                assert myProperty2.getType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty()
                assert myProperty2.getGenericType().getAnnotationNames().isEmpty()
                assert myProperty2.getGenericType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty()

                // Validate the cache is working
                assert context.getClassElement("python.AnnotationPropertyClass").get()
                        .getBeanProperties().stream().filter {p -> (p.name == "myProp2") }.findFirst().get()
                        .getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT]

                assert context.getClassElement("python.MyBean1").get().getAnnotationMetadata().isEmpty()
            }
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }

}

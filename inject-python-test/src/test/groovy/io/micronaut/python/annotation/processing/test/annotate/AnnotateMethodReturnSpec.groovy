package io.micronaut.python.annotation.processing.test.annotate

import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class AnnotateMethodReturnSpec extends AbstractPythonTypeElementSpec {

    void 'test annotating'() {
        when:
            def definition = buildBeanDefinition('python', 'AnnotateMethodReturnClass', '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

class MyBean1:
    name: str

@Singleton
class AnnotateMethodReturnClass:

    @Executable
    def myMethod1(self) -> MyBean1:
        return nil

    @Executable
    def myMethod2(self,) -> MyBean1:
        return nil

''')
        then:
            validate(definition)
    }

    void validate(BeanDefinition definition) {
        def method1 = definition.getRequiredMethod("myMethod1")
        def method1ReturnType = method1.getReturnType()

        assert method1ReturnType.simpleName == "MyBean1"
        assert method1ReturnType.getAnnotationMetadata().hasAnnotation(MyAnnotation)
        assert method1ReturnType.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation)
        assert !method1.hasAnnotation(MyAnnotation)

        def method2 = definition.getRequiredMethod("myMethod2")
        def method2ReturnType = method2.getReturnType()

        assert !method2ReturnType.getAnnotationMetadata().hasAnnotation(MyAnnotation)
        assert !method2ReturnType.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation)
        assert !method2.hasAnnotation(MyAnnotation)
        assert !method2.hasAnnotation(MyAnnotation)
    }

    static class AnnotateMethodReturnVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement classElement, VisitorContext context) {
            if (classElement.getClass().getName().containsIgnoreCase("java")) {
                return
            }
            if (classElement.getSimpleName() == "AnnotateMethodReturnClass") {

                def myMethod1 = classElement.findMethod("myMethod1").get()
                def returnType = myMethod1.getReturnType()
                def genericReturnType = myMethod1.getGenericReturnType()
                if (returnType instanceof GenericPlaceholderElement) {
                    assert genericReturnType instanceof GenericPlaceholderElement
                    def placeholderElement = returnType as GenericPlaceholderElement
                    def genericPlaceholderElement = genericReturnType as GenericPlaceholderElement
                    assert placeholderElement.getGenericNativeType() == genericPlaceholderElement.getGenericNativeType()
                    assert placeholderElement.variableName == genericPlaceholderElement.variableName
//                    if ("K" == placeholderElement.variableName) {
//                        assert placeholderElement.declaringElement.get() == myMethod1
//                        assert genericPlaceholderElement.declaringElement.get() == myMethod1
//                    } else {
//                        assert placeholderElement.declaringElement.get() == classElement
//                        assert genericPlaceholderElement.declaringElement.get() == classElement
//                    }
                }

                assert returnType.getAnnotationMetadata().getAnnotationNames().isEmpty()
                assert genericReturnType.getAnnotationMetadata().getAnnotationNames().isEmpty()

                returnType.annotate(MyAnnotation)

                assert returnType.getAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                assert genericReturnType.getAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                // The annotation should be added to type annotations
                assert returnType.getTypeAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                assert genericReturnType.getTypeAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                // The annotation is not added to the actual type
                assert returnType.getType().isEmpty()
                assert genericReturnType.getType().isEmpty()

                // Validate the cache is working

                def newClassElement = context.getClassElement("python.AnnotateMethodReturnClass").get()
                def newMethod = newClassElement.findMethod("myMethod1").get()
                def newReturnType = newMethod.getReturnType()
                def newGenericReturnType = newMethod.getGenericReturnType()

                validateBeanType(newGenericReturnType.getType())

                assert newReturnType.getAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                assert newReturnType.getTypeAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                assert newGenericReturnType.getAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                assert newGenericReturnType.getTypeAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]

                // Validate the annotation is not added to the return class type of myMethod2

                def method2ReturnType = newClassElement.findMethod("myMethod2").get().getReturnType()
                def method2GenericReturnType = newClassElement.findMethod("myMethod2").get().getGenericReturnType()

                validateBeanType(method2GenericReturnType.getType())

                assert method2ReturnType.getAnnotationMetadata().isEmpty()
                assert method2ReturnType.getTypeAnnotationMetadata().isEmpty()

                assert method2GenericReturnType.getAnnotationMetadata().isEmpty()
                assert method2GenericReturnType.getTypeAnnotationMetadata().isEmpty()

                assert method2ReturnType.getTypeAnnotationMetadata().isEmpty()
                assert method2ReturnType.getAnnotationMetadata().isEmpty()

                assert method2GenericReturnType.getTypeAnnotationMetadata().isEmpty()
                assert method2GenericReturnType.getAnnotationMetadata().isEmpty()

                def bean = context.getClassElement("python.MyBean1").get()
                validateBeanType(bean)
            }
        }

        private static void validateBeanType(ClassElement bean) {
            assert bean.getAnnotationMetadata().isEmpty()
            assert bean.getTypeAnnotationMetadata().isEmpty()
//            assert bean.getMethods().size() == 2
//            assert bean.getFields().size() == 1
        }
    }

}

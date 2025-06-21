package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class AnnotateMethodReturnSpec extends AbstractKotlinCompilerSpec {

    void 'test annotating'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateMethodReturnClass', '''
package addann;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Bean;

@Bean
class AnnotateMethodReturnClass {

    @Executable
    fun myMethod1() : MyBean1? {
        return null
    }

    @Executable
    fun myMethod2() : MyBean1? {
        return null
    }

}

class MyBean1(var name: String)
''')
        then:
            validate(definition)
    }

    void 'test annotating 2'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateMethodReturnClass', '''
package addann;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Bean;

@Bean
class AnnotateMethodReturnClass<T : MyBean1> {

    @Executable
    fun myMethod1() : T? {
        return null
    }

    @Executable
    fun myMethod2() : T? {
        return null
    }

}

class MyBean1(var name: String)

''')
        then:
            validate(definition)
    }

    void 'test annotating 3'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateMethodReturnClass', '''
package addann;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Bean;

@Bean
class AnnotateMethodReturnClass<T : MyBean1> {

    @Executable
    fun <K : T> myMethod1() : K? {
        return null
    }

    @Executable
    fun <K : T> myMethod2() : K? {
        return null
    }

}

class MyBean1(public var name: String)
''')
        then:
            validate(definition)
    }

    void 'test annotating 4'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateMethodReturnClass', '''
package addann;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Bean;

abstract class BaseAnnotateMethodReturnClass<S> {

    @Executable
    fun myMethod1() : S? {
        return null
    }

    @Executable
    fun myMethod2() : S? {
        return null
    }

}

@Bean
class AnnotateMethodReturnClass<K : MyBean1> : BaseAnnotateMethodReturnClass<K>()

class MyBean1(var name: String)
''')
        then:
            validate(definition)
    }

    void 'test annotating 6'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateMethodReturnClass', '''
package addann;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Bean;

abstract class BaseAnnotateMethodReturnClass<S> {

    @Executable
    fun myMethod1() : S? {
        return null
    }

    @Executable
    fun myMethod2() : S? {
        return null
    }
}

@Bean
class AnnotateMethodReturnClass : BaseAnnotateMethodReturnClass<MyBean1>()

class MyBean1(var name: String)
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
        void visitClass(ClassElement classElement, VisitorContext context) {
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

                def newClassElement = context.getClassElement("addann.AnnotateMethodReturnClass").get()
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

                assert method2ReturnType.getAnnotationMetadata().getAnnotationNames().asList() == []
                assert method2ReturnType.getTypeAnnotationMetadata().getAnnotationNames().asList() == []

                assert method2GenericReturnType.getAnnotationMetadata().getAnnotationNames().asList() == []
                assert method2GenericReturnType.getTypeAnnotationMetadata().getAnnotationNames().asList() == []

                assert method2ReturnType.getTypeAnnotationMetadata().getAnnotationNames().asList() == []
                assert method2ReturnType.getAnnotationMetadata().getAnnotationNames().asList() == []

                assert method2GenericReturnType.getTypeAnnotationMetadata().getAnnotationNames().asList() == []
                assert method2GenericReturnType.getAnnotationMetadata().getAnnotationNames().asList() == []

                def bean = context.getClassElement("addann.MyBean1").get()
                validateBeanType(bean)
            }
        }

        private static void validateBeanType(ClassElement bean) {
            assert bean.isAssignable("addann.MyBean1")
            assert bean.getAnnotationMetadata().isEmpty()
            assert bean.getTypeAnnotationMetadata().isEmpty()
            assert bean.getMethods().size() == 0
            assert bean.getFields().size() == 1
        }
    }

}

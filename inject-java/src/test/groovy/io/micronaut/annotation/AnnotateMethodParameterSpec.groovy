package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class AnnotateMethodParameterSpec extends AbstractTypeElementSpec {

    void 'test annotating'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateMethodParameterClass', '''
package addann;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Bean;

@Bean
class AnnotateMethodParameterClass {

    @Executable
    public MyBean1 myMethod1(MyBean1 param) {
        return null;
    }

    @Executable
    public MyBean1 myMethod2(MyBean1 param) {
        return null;
    }

}

class MyBean1 {
}
''')
        then:
            validate(definition)
    }

    void 'test annotating 2'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateMethodParameterClass', '''
package addann;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Bean;

@Bean
class AnnotateMethodParameterClass<T extends MyBean1> {

    @Executable
    public T myMethod1(T param) {
        return null;
    }

    @Executable
    public T myMethod2(T param) {
        return null;
    }

}

class MyBean1 {
}
''')
        then:
            validate(definition)
    }

    void 'test annotating 3'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateMethodParameterClass', '''
package addann;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Bean;

@Bean
class AnnotateMethodParameterClass<T extends MyBean1> {

    @Executable
    public <K extends T> K myMethod1(K param) {
        return null;
    }

    @Executable
    public <K extends T> K myMethod2(K param) {
        return null;
    }

}

class MyBean1 {
}
''')
        then:
            validate(definition)
    }

    void 'test annotating 4'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateMethodParameterClass', '''
package addann;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Bean;

abstract class Base<S> {

    @Executable
    public S myMethod1(S param) {
        return null;
    }

    @Executable
    public S myMethod2(S param) {
        return null;
    }

}

@Bean
class AnnotateMethodParameterClass<K extends MyBean1> extends Base<K> {
}

class MyBean1 {
}
''')
        then:
            validate(definition)
    }

    void 'test annotating 6'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateMethodParameterClass', '''
package addann;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Bean;

abstract class Base<S> {

    @Executable
    public S myMethod1(S param) {
        return null;
    }

    @Executable
    public S myMethod2(S param) {
        return null;
    }

}

@Bean
class AnnotateMethodParameterClass extends Base<MyBean1> {
}

class MyBean1 {
}
''')
        then:
            validate(definition)
    }

    void validate(BeanDefinition definition) {
        def method1 = definition.findPossibleMethods("myMethod1").findAny().get()
        def method1ParameterType = method1.getArguments()[0]
        def method1ReturnType = method1.getReturnType()

        assert method1ParameterType.simpleName == "MyBean1"
        assert method1ReturnType.simpleName == "MyBean1"
        assert method1ParameterType.getAnnotationMetadata().hasAnnotation(MyAnnotation)
        assert !method1ReturnType.getAnnotationMetadata().hasAnnotation(MyAnnotation)
        assert !method1ReturnType.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation)
        assert !method1.hasAnnotation(MyAnnotation)

        def method2 = definition.findPossibleMethods("myMethod2").findAny().get()
        def method2ParameterType = method2.getArguments()[0]
        def method2ReturnType = method2.getReturnType()

        assert !method2ParameterType.getAnnotationMetadata().hasAnnotation(MyAnnotation)
        assert !method2ReturnType.getAnnotationMetadata().hasAnnotation(MyAnnotation)
        assert !method2ReturnType.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation)
        assert !method2.hasAnnotation(MyAnnotation)
        assert !method2.hasAnnotation(MyAnnotation)
    }

    static class AnnotateMethodParameterVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitClass(ClassElement classElement, VisitorContext context) {
            if (classElement.getSimpleName() == "AnnotateMethodParameterClass") {

                def myMethod1 = classElement.findMethod("myMethod1").get()
                def type = myMethod1.getParameters()[0].getType()
                def genericType = myMethod1.getParameters()[0].getGenericType()
                if (type instanceof GenericPlaceholderElement) {
                    assert genericType instanceof GenericPlaceholderElement
                    def placeholderElement = type as GenericPlaceholderElement
                    def genericPlaceholderElement = genericType as GenericPlaceholderElement
                    assert placeholderElement.getGenericNativeType() == genericPlaceholderElement.getGenericNativeType()
                    assert placeholderElement.variableName == genericPlaceholderElement.variableName
                }

                assert type.getAnnotationMetadata().getAnnotationNames().isEmpty()
                assert genericType.getAnnotationMetadata().getAnnotationNames().isEmpty()

                type.annotate(MyAnnotation)

                assert type.getAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                assert genericType.getAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                // The annotation should be added to type annotations
                assert type.getTypeAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                assert genericType.getTypeAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                // The annotation is not added to the actual type
                assert type.getType().isEmpty()
                assert genericType.getType().isEmpty()
                myMethod1.getReturnType().getAnnotationMetadata().isEmpty()
                myMethod1.getGenericReturnType().getAnnotationMetadata().isEmpty()

                // Validate the cache is working

                def newClassElement = context.getClassElement("addann.AnnotateMethodParameterClass").get()
                def newMethod = newClassElement.findMethod("myMethod1").get()
                def newType = newMethod.getParameters()[0].getType()
                def newGenericType =newMethod.getParameters()[0].getGenericType()

                assert newType.getAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                assert newType.getTypeAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                assert newGenericType.getAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                assert newGenericType.getTypeAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]

                assert context.getClassElement("addann.MyBean1").get().getAnnotationMetadata().isEmpty()
                assert context.getClassElement("addann.MyBean1").get().getTypeAnnotationMetadata().isEmpty()

                // Validate the annotation is not added to the return class type of myMethod2

                def method2Type = newClassElement.findMethod("myMethod2").get().getParameters()[0].getType()
                def method2GenericType = newClassElement.findMethod("myMethod2").get().getParameters()[0].getGenericType()

                assert method2Type.getAnnotationMetadata().isEmpty()
                assert method2Type.getTypeAnnotationMetadata().isEmpty()

                assert method2GenericType.getAnnotationMetadata().isEmpty()
                assert method2GenericType.getTypeAnnotationMetadata().isEmpty()

                assert method2Type.getTypeAnnotationMetadata().isEmpty()
                assert method2Type.getAnnotationMetadata().isEmpty()

                assert method2GenericType.getTypeAnnotationMetadata().isEmpty()
                assert method2GenericType.getAnnotationMetadata().isEmpty()

            }
        }
    }

}

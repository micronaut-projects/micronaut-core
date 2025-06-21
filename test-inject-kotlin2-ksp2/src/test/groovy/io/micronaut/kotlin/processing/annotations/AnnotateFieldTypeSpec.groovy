package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class AnnotateFieldTypeSpec extends AbstractKotlinCompilerSpec {

    void 'test annotating'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateFieldTypeClass', '''
package addann;

import io.micronaut.context.annotation.Bean;
import jakarta.inject.Inject;

@Bean
class AnnotateFieldTypeClass {

    @Inject
    var myField1: MyBean1? = null

    @Inject
    var myField2: MyBean1? = null

}

class MyBean1(var name: String)

''')
        then:
            validate(definition)
    }

    void 'test annotating 2'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateFieldTypeClass', '''
package addann;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Bean;
import jakarta.inject.Inject;

@Bean
class AnnotateFieldTypeClass<T : MyBean1> {

    @Inject
    var myField1: T? = null

    @Inject
    var myField2: T? = null

}

class MyBean1(var name: String)
''')
        then:
            validate(definition)
    }

    void 'test annotating 3'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateFieldTypeClass', '''
package addann;

import io.micronaut.context.annotation.Bean;
import jakarta.inject.Inject;

abstract class BaseAnnotateFieldTypeClass<S> {

    @Inject
    var myField1: S? = null

    @Inject
    var myField2: S? = null

}

@Bean
class AnnotateFieldTypeClass<K : MyBean1> : BaseAnnotateFieldTypeClass<K>()

class MyBean1(var name: String)

''')
        then:
            validate(definition)
    }

    void 'test annotating 4'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateFieldTypeClass', '''
package addann;

import io.micronaut.context.annotation.Bean;
import jakarta.inject.Inject;

abstract class BaseAnnotateFieldTypeClass<S> {

    @Inject
    var myField1: S? = null

    @Inject
    var myField2: S? = null

}

@Bean
class AnnotateFieldTypeClass : BaseAnnotateFieldTypeClass<MyBean1>()

class MyBean1(var name: String)
''')
        then:
            validate(definition)
    }

    void validate(BeanDefinition definition) {
        def inject1 = definition.getInjectedMethods()[0]
        assert inject1.name == "setMyField1"
        // TODO: support annotations propagation
        assert !inject1.getAnnotationMetadata().hasAnnotation(MyAnnotation)

        def inject2 = definition.getInjectedMethods()[1]
        assert inject2.name == "setMyField2"
        assert !inject2.getAnnotationMetadata().hasAnnotation(MyAnnotation)
    }

    static class AnnotateFieldTypeVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitClass(ClassElement classElement, VisitorContext context) {
            if (classElement.getSimpleName() == "AnnotateFieldTypeClass") {

                def myField1 = classElement.findField("myField1").get()
                def type = myField1.getType()
                def genericType = myField1.getGenericType()
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

                // Validate the cache is working

                def newClassElement = context.getClassElement("addann.AnnotateFieldTypeClass").get()
                def newField = newClassElement.findField("myField1").get()
                def newType = newField.getType()
                def newGenericType = newField.getGenericType()

                validateBeanType(newGenericType.getType())

                assert newType.getAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                assert newType.getTypeAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                assert newGenericType.getAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                assert newGenericType.getTypeAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]

                // Validate the annotation is not added to the return class type of myMethod2

                def field2Type = newClassElement.findField("myField2").get().getType()
                def field2GenericType = newClassElement.findField("myField2").get().getGenericType()

                validateBeanType(field2GenericType.getType())

                assert field2Type.getAnnotationMetadata().isEmpty()
                assert field2Type.getTypeAnnotationMetadata().isEmpty()

                assert field2GenericType.getAnnotationMetadata().isEmpty()
                assert field2GenericType.getTypeAnnotationMetadata().isEmpty()

                assert field2Type.getTypeAnnotationMetadata().isEmpty()
                assert field2Type.getAnnotationMetadata().isEmpty()

                assert field2GenericType.getTypeAnnotationMetadata().isEmpty()
                assert field2GenericType.getAnnotationMetadata().isEmpty()

                def bean = context.getClassElement("addann.MyBean1").get()
                validateBeanType(bean)
            }

        }

        private static void validateBeanType(ClassElement bean) {
            assert bean.getAnnotationMetadata().isEmpty()
            assert bean.getTypeAnnotationMetadata().isEmpty()
            assert bean.getMethods().size() == 0
            assert bean.getFields().size() == 1
        }
    }

}

package io.micronaut.inject.annotation.modify

import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.visitor.AllElementsVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

// Annotating the field type doesn't add the annotation to the runtime inject field
class AnnotateFieldTypeSpec extends AbstractBeanDefinitionSpec {

    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, AnnotateFieldTypeVisitor.name)
    }

    def cleanup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, "")
        AllElementsVisitor.clearVisited()
    }

    void 'test annotating'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateFieldTypeClass', '''
package addann

import io.micronaut.context.annotation.Bean
import jakarta.inject.Inject

@Bean
class AnnotateFieldTypeClass {

    @Inject
    public MyBean1 myField1

    @Inject
    public MyBean1 myField2

}

class MyBean1 {
}
''')
        then:
            validate(definition)
    }

    void 'test annotating 2'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateFieldTypeClass', '''
package addann

import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Bean
import jakarta.inject.Inject

@Bean
class AnnotateFieldTypeClass<T extends MyBean1> {

    @Inject
    public T myField1

    @Inject
    public T myField2

}

class MyBean1 {
}
''')
        then:
            validate(definition)
    }

    void 'test annotating 3'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateFieldTypeClass', '''
package addann

import io.micronaut.context.annotation.Bean
import jakarta.inject.Inject

abstract class BaseAnnotateFieldTypeClass<S> {

    @Inject
    public S myField1

    @Inject
    public S myField2

}

@Bean
class AnnotateFieldTypeClass<K extends MyBean1> extends BaseAnnotateFieldTypeClass<K> {
}

class MyBean1 {
}
''')
        then:
            validate(definition)
    }

    void 'test annotating 4'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateFieldTypeClass', '''
package addann

import io.micronaut.context.annotation.Bean
import jakarta.inject.Inject

abstract class BaseAnnotateFieldTypeClass<S> {

    @Inject
    public S myField1

    @Inject
    public S myField2

}

@Bean
class AnnotateFieldTypeClass extends BaseAnnotateFieldTypeClass<MyBean1> {
}

class MyBean1 {
}
''')
        then:
            validate(definition)
    }

    void validate(BeanDefinition definition) {
        def myField1 = definition.getInjectedFields()[0]
        assert myField1.name == "myField1"
        assert !myField1.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation)

        def myField2 = definition.getInjectedFields()[1]
        assert myField2.name == "myField2"
        assert !myField2.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation)
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

                assert newType.getAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                assert newType.getTypeAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                assert newGenericType.getAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                assert newGenericType.getTypeAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]

                assert context.getClassElement("addann.MyBean1").get().getAnnotationMetadata().isEmpty()
                assert context.getClassElement("addann.MyBean1").get().getTypeAnnotationMetadata().isEmpty()

                // Validate the annotation is not added to the return class type of myMethod2

                def field2Type = newClassElement.findField("myField2").get().getType()
                def field2GenericType = newClassElement.findField("myField2").get().getGenericType()

                assert field2Type.getAnnotationMetadata().isEmpty()
                assert field2Type.getTypeAnnotationMetadata().isEmpty()

                assert field2GenericType.getAnnotationMetadata().isEmpty()
                assert field2GenericType.getTypeAnnotationMetadata().isEmpty()

                assert field2Type.getTypeAnnotationMetadata().isEmpty()
                assert field2Type.getAnnotationMetadata().isEmpty()

                assert field2GenericType.getTypeAnnotationMetadata().isEmpty()
                assert field2GenericType.getAnnotationMetadata().isEmpty()

            }
        }
    }

}

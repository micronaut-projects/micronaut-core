package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class AnnotateFieldSpec extends AbstractTypeElementSpec {

    void 'test annotating'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotationFieldClass', '''
package addann;

import io.micronaut.context.annotation.Bean;
import jakarta.inject.Inject;

@Bean
class AnnotationFieldClass {

    @Inject
    public MyBean1 myField1;

    @Inject
    public MyBean1 myField2;

}

class MyBean1 {
}

''')
        then:
            def myField1 = definition.getInjectedFields()[0]
            myField1.name == "myField1"
            myField1.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation)
        and:
            def myField2 = definition.getInjectedFields()[1]
            myField2.name == "myField2"
            !myField2.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation)
    }

    static class AnnotationFieldVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.getSimpleName() == "AnnotationFieldClass") {
                def myField1 = element.findField("myField1").get()
                assert myField1.getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT]
                myField1.annotate(MyAnnotation)
                assert myField1.getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT, MyAnnotation.class.name]
                assert myField1.getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT, MyAnnotation.class.name]
                assert myField1.getType().getAnnotationNames().isEmpty()
                assert myField1.getType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty()
                assert myField1.getType().getType().getAnnotationMetadata().isEmpty()
                assert myField1.getGenericType().getAnnotationNames().isEmpty()
                assert myField1.getGenericType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty()
                assert myField1.getGenericType().getType().getAnnotationMetadata().isEmpty()

                // Validate the cache is working
                assert context.getClassElement("addann.AnnotationFieldClass").get()
                        .findField("myField1").get()
                        .getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT, MyAnnotation.class.name]

                // Test the second method with the same type doesn't have the annotations

                def myField2 = element.findField("myField2").get()
                assert myField2.getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT]
                assert myField2.getType().getAnnotationNames().isEmpty()
                assert myField2.getType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty()
                assert myField2.getGenericType().getAnnotationNames().isEmpty()
                assert myField2.getGenericType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty()

                // Validate the cache is working
                assert context.getClassElement("addann.AnnotationFieldClass").get()
                        .findField("myField2").get()
                        .getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT]

                assert context.getClassElement("addann.MyBean1").get().getAnnotationMetadata().isEmpty()
            }
        }
    }

}

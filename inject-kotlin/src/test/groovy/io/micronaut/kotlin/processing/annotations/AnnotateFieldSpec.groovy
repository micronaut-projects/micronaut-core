package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import spock.lang.PendingFeature

class AnnotateFieldSpec extends AbstractKotlinCompilerSpec {

    void 'test annotating'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotationFieldClass', '''
package addann;

import io.micronaut.context.annotation.Bean;
import jakarta.inject.Inject;

@Bean
class AnnotationFieldClass {

    @Inject
    var myField1: MyBean1? = null

    @Inject
    var myField2: MyBean1? = null

}

class MyBean1

''')
        then:
            def inject1 = definition.getInjectedMethods()[0]
            inject1.name == "setMyField1"
//            inject1.getAnnotationMetadata().hasAnnotation(MyAnnotation)
        and:
            def inject2 = definition.getInjectedMethods()[1]
            inject2.name == "setMyField2"
            !inject2.getAnnotationMetadata().hasAnnotation(MyAnnotation)
    }

    @PendingFeature
    void 'test annotating is preserved'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotationFieldClass', '''
package addann;

import io.micronaut.context.annotation.Bean;
import jakarta.inject.Inject;

@Bean
class AnnotationFieldClass {

    @Inject
    var myField1: MyBean1? = null

    @Inject
    var myField2: MyBean1? = null

}

class MyBean1

''')
        then:
            def inject1 = definition.getInjectedMethods()[0]
            inject1.name == "setMyField1"
            inject1.getAnnotationMetadata().hasAnnotation(MyAnnotation)
        and:
            def inject2 = definition.getInjectedMethods()[1]
            inject2.name == "setMyField2"
            !inject2.getAnnotationMetadata().hasAnnotation(MyAnnotation)
    }

    static class AnnotationFieldVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.getSimpleName() == "AnnotationFieldClass") {
                def myField1 = element.findField("myField1").get()
                assert myField1.getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT, AnnotationUtil.NULLABLE]
                myField1.annotate(MyAnnotation)
                assert myField1.getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT, AnnotationUtil.NULLABLE, MyAnnotation.class.name]
                assert myField1.getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT, AnnotationUtil.NULLABLE, MyAnnotation.class.name]
                assert myField1.getType().getAnnotationNames().isEmpty()
                assert myField1.getType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty()
                assert myField1.getType().getType().getAnnotationMetadata().isEmpty()
                assert myField1.getGenericType().getAnnotationNames().isEmpty()
                assert myField1.getGenericType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty()
                assert myField1.getGenericType().getType().getAnnotationMetadata().isEmpty()

                // Validate the cache is working
                assert context.getClassElement("addann.AnnotationFieldClass").get()
                        .findField("myField1").get()
                        .getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT, AnnotationUtil.NULLABLE, MyAnnotation.class.name]

                // Test the second method with the same type doesn't have the annotations

                def myField2 = element.findField("myField2").get()
                assert myField2.getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT, AnnotationUtil.NULLABLE]
                assert myField2.getType().getAnnotationNames().isEmpty()
                assert myField2.getType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty()
                assert myField2.getGenericType().getAnnotationNames().isEmpty()
                assert myField2.getGenericType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty()

                // Validate the cache is working
                assert context.getClassElement("addann.AnnotationFieldClass").get()
                        .findField("myField2").get()
                        .getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.INJECT, AnnotationUtil.NULLABLE]

                assert context.getClassElement("addann.MyBean1").get().getAnnotationMetadata().isEmpty()
            }
        }
    }

}

package io.micronaut.inject.visitor

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.groovy.TypeElementVisitorTransform
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.writer.BeanDefinitionVisitor

class ElementAnnotateSpec extends AbstractBeanDefinitionSpec{

    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, MyAnnotatingTypeElementVisitor.name)
    }

    def cleanup() {
        AllElementsVisitor.clearVisited()
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, "")
    }

    void "test annotate introduction advice"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.context.annotation.*;
import java.net.*;
import javax.validation.constraints.*;
import io.micronaut.aop.introduction.Stub;

@Stub
interface MyInterface{
    void save(@NotBlank String name, @Min(1L) int age);
    void saveTwo(String name);
}

''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.getRequiredMethod("save", String, int).hasAnnotation("foo.bar.Ann")
        beanDefinition.getRequiredMethod("saveTwo", String).hasAnnotation("foo.bar.Ann")
    }

    void "test that elements can be dynamically annotated at compilation time"() {
        given:
        def definition = buildBeanDefinition('test.TestListener', '''
package test;
import io.micronaut.context.annotation.*;
import javax.inject.Singleton;

@Singleton
class TestListener {

    @Executable
    void receive(String v) {
    }
}

''')

        expect:
        definition.hasAnnotation("foo.bar.Ann")
        definition.getValue("foo.bar.Ann", "foo", String).get() == 'bar'
        definition.findMethod("receive", String).get().hasAnnotation('foo.bar.Ann')
    }

    static class MyAnnotatingTypeElementVisitor implements TypeElementVisitor {
        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            element.annotate("foo.bar.Ann") { AnnotationValueBuilder builder ->
                builder.member("foo", "bar")
            }
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            element.annotate("foo.bar.Ann") { AnnotationValueBuilder builder ->
                builder.member("foo", "bar")
            }
        }
    }
}

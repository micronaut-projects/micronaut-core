package io.micronaut.inject.annotation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

class SourceAnnotationsSpec extends AbstractBeanDefinitionSpec {

    private static final String SOURCE = '''
package test

import java.lang.annotation.*

@MyRepeatable("a")
@MyRepeatable("b")
@jakarta.inject.Singleton
class MyBean {

    @MyRepeatable("single")
    @Defaults(name = "x")
    public String field

    @MyRepeatables([@MyRepeatable("c1"), @MyRepeatable("c2")])
    public String containerField

    @MyRepeatable("m")
    public String method(@MyRepeatable("p") String param) {
        return null
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE])
@Repeatable(MyRepeatables)
@interface MyRepeatable {
    String value()
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE])
@interface MyRepeatables {
    MyRepeatable[] value()
}

@Retention(RetentionPolicy.SOURCE)
@interface Defaults {
    String name()
    String description() default ""
    int count() default 3
}
'''

    void "test the source view of a class keeps repeatable annotations as written and adds no stereotype"() {
        given:
        def element = buildClassElement('test.MyBean', SOURCE)

        when:
        def source = element.getSourceAnnotations()

        then: "two repetitions arrive in the container the Groovy compiler synthesizes for them"
        names(source) == ['jakarta.inject.Singleton', 'test.MyRepeatables']
        nested(source[1]) == ['a', 'b']
        source.every { it.retentionPolicy == RetentionPolicy.RUNTIME }

        and: "the metadata adds the scope stereotype, the source view does not"
        element.getAnnotationMetadata().hasDeclaredStereotype(AnnotationUtil.SCOPE)
        !source.any { it.annotationName == AnnotationUtil.SCOPE }
    }

    void "test the source view of a field carries the defaults for the members it left out, empty ones included"() {
        given:
        def element = buildClassElement('test.MyBean', SOURCE)
        def field = element.getFields().find { it.name == 'field' }

        when:
        def source = field.getSourceAnnotations()

        then:
        names(source) == ['test.MyRepeatable', 'test.Defaults']
        source[0].stringValue().get() == 'single'

        and:
        AnnotationValue<?> defaults = source[1]
        defaults.retentionPolicy == RetentionPolicy.SOURCE
        defaults.stringValue('name').get() == 'x'
        defaults.getDefaultValues().get('description') == ''
        defaults.getDefaultValues().get('count') == 3
        !defaults.getValues().containsKey('description')
    }

    void "test a container the source wrote is the container"() {
        given:
        def element = buildClassElement('test.MyBean', SOURCE)
        def field = element.getFields().find { it.name == 'containerField' }

        when:
        def source = field.getSourceAnnotations()

        then:
        names(source) == ['test.MyRepeatables']
        nested(source[0]) == ['c1', 'c2']

        and:
        field.getAnnotationMetadata().getAnnotationValuesByName('test.MyRepeatable').size() == 2
    }

    void "test a method and its parameter"() {
        given:
        def element = buildClassElement('test.MyBean', SOURCE)
        def method = element.getMethods().find { it.name == 'method' }

        expect:
        names(method.getSourceAnnotations()) == ['test.MyRepeatable']
        method.getSourceAnnotations()[0].stringValue().get() == 'm'
        names(method.getParameters()[0].getSourceAnnotations()) == ['test.MyRepeatable']
        method.getParameters()[0].getSourceAnnotations()[0].stringValue().get() == 'p'

        and:
        method.getAnnotationMetadata().hasAnnotation('jakarta.inject.Singleton')
        !method.getSourceAnnotations().any { it.annotationName == 'jakarta.inject.Singleton' }
    }

    void "test what a visitor adds does not appear and the meta-annotations of an annotation interface do"() {
        given:
        def element = buildClassElement('test.MyAnn', '''
package test

import java.lang.annotation.*

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@interface MyAnn {
}
''')

        when:
        element.annotate('test.Added')

        then:
        element.getAnnotationMetadata().hasDeclaredAnnotation('test.Added')
        !element.getAnnotationMetadata().hasDeclaredAnnotation(Retention.name)
        names(element.getSourceAnnotations()) == [Retention.name, Target.name]
        element.getSourceAnnotations()[0].stringValue().get() == 'SOURCE'
    }

    private static List<String> names(List<AnnotationValue<?>> values) {
        return values*.annotationName
    }

    private static List<String> nested(AnnotationValue<?> container) {
        return (container.getValues().get('value') as AnnotationValue[]).collect { it.stringValue().get() }
    }
}

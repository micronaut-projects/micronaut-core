package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

class SourceAnnotationsSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import java.lang.annotation.*;

@MyRepeatable("a")
@MyRepeatable("b")
@jakarta.inject.Singleton
class MyBean<@TypeAnn("class-var") T> {

    @MyRepeatable("single")
    @Defaults(name = "x")
    public @TypeAnn("field-type") String field;

    @MyRepeatables({@MyRepeatable("c1"), @MyRepeatable("c2")})
    public T typeVarField;

    public @TypeAnn("component") String @TypeAnn("outer") [] @TypeAnn("inner") [] arrayField;

    @MyRepeatable("m")
    public <@TypeAnn("method-var") M> M method(@MyRepeatable("p") String param) {
        return null;
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE_USE, ElementType.TYPE_PARAMETER, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
@Repeatable(MyRepeatables.class)
@interface MyRepeatable {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE_USE, ElementType.TYPE_PARAMETER, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
@interface MyRepeatables {
    MyRepeatable[] value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@interface TypeAnn {
    String value() default "";
}

@Retention(RetentionPolicy.SOURCE)
@interface Defaults {
    String name();
    String description() default "";
    String[] tags() default {};
    int count() default 3;
}
'''

    void "test the source view of a class keeps repeatable annotations as written and adds no stereotype"() {
        given:
        def element = buildClassElement(SOURCE)

        when:
        def source = element.getSourceAnnotations()

        then: "in source order; two repetitions arrive in the container javac synthesizes for them"
        names(source) == ['test.MyRepeatables', 'jakarta.inject.Singleton']
        nested(source[0]) == ['a', 'b']
        source.every { it.retentionPolicy == RetentionPolicy.RUNTIME }

        and: "the metadata adds the scope stereotype, the source view does not"
        element.getAnnotationMetadata().hasDeclaredStereotype(AnnotationUtil.SCOPE)
        !source.any { it.annotationName == AnnotationUtil.SCOPE }
    }

    void "test the source view of a field carries the defaults for the members it left out, empty ones included"() {
        given:
        def element = buildClassElement(SOURCE)
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
        defaults.getDefaultValues().keySet()*.toString().sort() == ['count', 'description', 'tags']
        defaults.getDefaultValues().get('description') == ''
        defaults.getDefaultValues().get('tags') == [] as String[]
        defaults.getDefaultValues().get('count') == 3
        !defaults.getValues().containsKey('description')

        and: "the field type reports the type-use annotations; javac applies MyRepeatable, which targets FIELD and TYPE_USE, to both"
        names(field.getType().getTypeAnnotationMetadata().getSourceAnnotations()) == ['test.MyRepeatable', 'test.TypeAnn']
        field.getType().getTypeAnnotationMetadata().getSourceAnnotations()[1].stringValue().get() == 'field-type'
    }

    void "test a container the source wrote is the container"() {
        given:
        def element = buildClassElement(SOURCE)
        def field = element.getFields().find { it.name == 'typeVarField' }

        when:
        def source = field.getSourceAnnotations()

        then:
        names(source) == ['test.MyRepeatables']
        nested(source[0]) == ['c1', 'c2']

        and: "the metadata sees the unwrapped repetitions"
        element.getAnnotationMetadata() != null
        field.getAnnotationMetadata().getAnnotationValuesByName('test.MyRepeatable').size() == 2
    }

    void "test a use of a type variable reports what the use wrote, its declaration the type parameter annotations"() {
        given:
        def element = buildClassElement(SOURCE)
        def field = element.getFields().find { it.name == 'typeVarField' }
        def use = (GenericPlaceholderElement) field.getGenericType()
        def declaration = element.getDeclaredGenericPlaceholders()[0]

        expect: "the metadata of an unannotated use still falls back to the declaration"
        use.getGenericTypeAnnotationMetadata().hasAnnotation('test.TypeAnn')

        and: "the source view does not"
        use.getGenericTypeAnnotationMetadata().getSourceAnnotations().isEmpty()
        use.getSourceAnnotations().isEmpty()

        and: "the declaration reports the annotations of the type parameter"
        names(declaration.getGenericTypeAnnotationMetadata().getSourceAnnotations()) == ['test.TypeAnn']
        declaration.getGenericTypeAnnotationMetadata().getSourceAnnotations()[0].stringValue().get() == 'class-var'
        names(declaration.getSourceAnnotations()) == ['test.TypeAnn']
    }

    void "test a method, its parameter and its declared type variable"() {
        given:
        def element = buildClassElement(SOURCE)
        def method = element.getMethods().find { it.name == 'method' }

        expect:
        names(method.getSourceAnnotations()) == ['test.MyRepeatable']
        method.getSourceAnnotations()[0].stringValue().get() == 'm'
        names(method.getParameters()[0].getSourceAnnotations()) == ['test.MyRepeatable']
        method.getParameters()[0].getSourceAnnotations()[0].stringValue().get() == 'p'

        and: "the method annotation metadata combines the class annotations, the source view does not"
        method.getAnnotationMetadata().hasAnnotation('jakarta.inject.Singleton')
        !method.getSourceAnnotations().any { it.annotationName == 'jakarta.inject.Singleton' }

        and:
        def typeVariable = method.getDeclaredTypeVariables()[0]
        names(typeVariable.getGenericTypeAnnotationMetadata().getSourceAnnotations()) == ['test.TypeAnn']
        typeVariable.getGenericTypeAnnotationMetadata().getSourceAnnotations()[0].stringValue().get() == 'method-var'
        ((GenericPlaceholderElement) method.getGenericReturnType()).getGenericTypeAnnotationMetadata().getSourceAnnotations().isEmpty()
    }

    void "test every dimension of an array reports its own type annotations while the metadata is unchanged"() {
        given:
        def element = buildClassElement(SOURCE)
        def arrayType = element.getFields().find { it.name == 'arrayField' }.getType()

        expect:
        arrayType.getArrayDimensions() == 2
        values(arrayType.getTypeAnnotationMetadata().getSourceAnnotations()) == ['outer']
        values(arrayType.fromArray().getTypeAnnotationMetadata().getSourceAnnotations()) == ['inner']
        values(arrayType.fromArray().fromArray().getTypeAnnotationMetadata().getSourceAnnotations()) == ['component']

        and: "the metadata keeps answering with the component's annotations, as before"
        arrayType.getTypeAnnotationMetadata().stringValue('test.TypeAnn').get() == 'component'
        arrayType.fromArray().getTypeAnnotationMetadata().stringValue('test.TypeAnn').get() == 'component'
        arrayType.fromArray().fromArray().getTypeAnnotationMetadata().stringValue('test.TypeAnn').get() == 'component'

        and: "an array made from the component holds no mirror for the new dimension"
        arrayType.fromArray().fromArray().toArray().getTypeAnnotationMetadata().getSourceAnnotations().isEmpty()
    }

    void "test what a visitor adds does not appear and the meta-annotations of an annotation interface do"() {
        given:
        def element = buildClassElement('''
package test;

import java.lang.annotation.*;

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
        element.getSourceAnnotations()[1].stringValues() == ['TYPE'] as String[]

        and: "a copy with preset metadata still reports what the source wrote"
        names(element.withAnnotationMetadata(element.getAnnotationMetadata()).getSourceAnnotations()) == [Retention.name, Target.name]
    }

    void "test an element no source backs reports nothing"() {
        expect:
        ClassElement.of(String).getSourceAnnotations().isEmpty()
        ClassElement.of('test.Missing').getSourceAnnotations().isEmpty()
    }

    private static List<String> names(List<AnnotationValue<?>> values) {
        return values*.annotationName
    }

    private static List<String> values(List<AnnotationValue<?>> values) {
        return values.collect { it.stringValue().get() }
    }

    private static List<String> nested(AnnotationValue<?> container) {
        return (container.getValues().get('value') as AnnotationValue[]).collect { it.stringValue().get() }
    }
}

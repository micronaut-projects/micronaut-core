package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.ClassElement

class NullMarkedArrayScopeSpec extends AbstractTypeElementSpec {

    void "the inferred non-null of a NullMarked array parameter does not leak to other declarations"() {
        when:
        def definition = buildBeanDefinition('test.Bean', '''
package test;
@jakarta.inject.Singleton
class Bean {
    @io.micronaut.context.annotation.Executable
    void m(String[] plain, @jakarta.annotation.Nullable String[] f) {}
}
@org.jspecify.annotations.NullMarked
@jakarta.inject.Singleton
class Marked {
    @io.micronaut.context.annotation.Executable
    String[] m(String[] a) { return a; }
}
''')
        def arguments = definition.executableMethods.find { it.methodName == 'm' }.arguments

        then:
        arguments[0].annotationMetadata.annotationNames.empty
        arguments[1].annotationMetadata.annotationNames as List == ['jakarta.annotation.Nullable']
    }

    void "the inferred non-null of NullMarked array fields, return types and parameters does not leak to other declarations"() {
        expect:
        buildClassElement("""
package test;
class Plain {
    Marked marked;
    String[] field;
    String[] method(String[] parameter) { return parameter; }
}
@org.jspecify.annotations.NullMarked
class Marked {
    String[] field;
    String[] method(String[] parameter) { return parameter; }
}
""") { ClassElement plain ->
            def marked = plain.fields.find { it.name == 'marked' }.type
            // touch the NullMarked declarations first so that any inferred non-null is recorded
            [types(marked).take(4), elements(marked)].flatten().each { assert it.nonNull }
            types(plain).each { type ->
                assert !type.nonNull
                assert !type.annotationMetadata.hasStereotype(AnnotationUtil.NON_NULL)
                assert !type.typeAnnotationMetadata.hasStereotype(AnnotationUtil.NON_NULL)
            }
            elements(plain).each { assert !it.nonNull }
            plain
        }
    }

    void "a dimension annotation of one array declaration does not leak to an unannotated one"() {
        expect:
        buildClassElement("""
package test;
import java.lang.annotation.*;
class Test {
    String @Mark("dimension") [] annotated;
    String[] plain;
}
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Mark { String value(); }
""") { ClassElement element ->
            def annotated = element.fields.find { it.name == 'annotated' }
            def plain = element.fields.find { it.name == 'plain' }
            assert annotated.type.typeAnnotationMetadata.stringValue('test.Mark').get() == 'dimension'
            assert !plain.type.typeAnnotationMetadata.hasAnnotation('test.Mark')
            assert !plain.genericType.typeAnnotationMetadata.hasAnnotation('test.Mark')
            element
        }
    }

    private static List types(ClassElement element) {
        def field = element.fields.find { it.name == 'field' }
        def method = element.methods.find { it.name == 'method' }
        [field.type, field.genericType, method.returnType, method.genericReturnType,
         method.parameters[0].type, method.parameters[0].genericType]
    }

    private static List elements(ClassElement element) {
        def field = element.fields.find { it.name == 'field' }
        def method = element.methods.find { it.name == 'method' }
        [field, method, method.parameters[0]]
    }
}

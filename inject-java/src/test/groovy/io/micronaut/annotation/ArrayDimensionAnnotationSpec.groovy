package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.PrimitiveElement

class ArrayDimensionAnnotationSpec extends AbstractTypeElementSpec {

    void "array dimensions retain their own annotations for #leaf"() {
        expect:
        buildClassElement("""
package test;
import java.lang.annotation.*;
import java.util.List;
class Test<T> {
    @Mark("leaf") $leaf [] @Mark("middle") [] @Mark("inner") [] field;
    @Mark("leaf") $leaf [] @Mark("middle") [] @Mark("inner") [] method(
        @Mark("leaf") $leaf [] @Mark("middle") [] @Mark("inner") [] parameter) { return null; }
}
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Mark { String value(); }
enum Choice { ONE }
""") { ClassElement element ->
            def field = element.fields.find { it.name == 'field' }
            def method = element.methods.find { it.name == 'method' }
            def types = [field.type, field.genericType, method.returnType, method.genericReturnType,
                         method.parameters[0].type, method.parameters[0].genericType]
            types.each { type ->
                assert annotations(type) == ['-', 'middle', 'inner', 'leaf']
                assert annotations(type.withArrayDimensions(1)) == ['inner', 'leaf']
                assert annotations(type.withArrayDimensions(0)) == ['leaf']
                assert annotations(type.toArray()) == ['-', '-', 'middle', 'inner', 'leaf']
                assert annotations(type) == ['-', 'middle', 'inner', 'leaf']
            }
            if (leaf.startsWith('List')) {
                assert field.genericType.withArrayDimensions(0).firstTypeArgument.get()
                    .typeAnnotationMetadata.stringValue('test.Mark').get() == 'argument'
                assert annotations(field.genericType.withTypeArguments([:])) == ['-', 'middle', 'inner', 'leaf']
            }
            element
        }

        where:
        leaf << ['String', 'int', 'T', 'Choice', 'List<@Mark("argument") String>']
    }

    void "mutating one dimension does not annotate its components for #leaf"() {
        expect:
        buildClassElement("""
package test;
class Test<T> { $leaf [][] field; }
enum Choice { ONE }
""") { ClassElement element ->
            def array = element.fields.find { it.name == 'field' }.genericType
            def component = array.fromArray()
            array.annotate('test.Outer')
            component.annotate('test.Inner')
            assert array.typeAnnotationMetadata.hasAnnotation('test.Outer')
            assert !array.typeAnnotationMetadata.hasAnnotation('test.Inner')
            assert component.typeAnnotationMetadata.hasAnnotation('test.Inner')
            assert !component.typeAnnotationMetadata.hasAnnotation('test.Outer')
            assert !component.fromArray().typeAnnotationMetadata.hasAnnotation('test.Outer')
            assert !component.fromArray().typeAnnotationMetadata.hasAnnotation('test.Inner')
            assert array.fromArray().typeAnnotationMetadata.hasAnnotation('test.Inner')
            assert PrimitiveElement.INT.typeAnnotationMetadata.empty
            element
        }

        where:
        leaf << ['String', 'int', 'T', 'Choice']
    }

    void "precise array annotations preserve legacy and jspecify nullability for #type"() {
        expect:
        buildClassElement("""
package test;
class Test {
    $type field;
    $type method($type parameter) { return null; }
}
""") { ClassElement element ->
            def field = element.fields.find { it.name == 'field' }
            def method = element.methods.find { it.name == 'method' }
            [field, method, method.parameters[0], field.type, field.genericType,
             method.returnType, method.genericReturnType, method.parameters[0].type].each {
                assert it.nullable == nullable
            }
            assert field.type.typeAnnotationMetadata.hasStereotype('jakarta.annotation.Nullable') == outerAnnotated
            assert field.type.fromArray().typeAnnotationMetadata.hasStereotype('jakarta.annotation.Nullable') == !outerAnnotated
            element
        }

        where:
        type                                                   | nullable | outerAnnotated
        '@io.micronaut.core.annotation.Nullable String[]'       |  true   | false
        'String @io.micronaut.core.annotation.Nullable []'      |  false  | true
        '@org.jspecify.annotations.Nullable String[]'           |  false  | false
        'String @org.jspecify.annotations.Nullable []'          |  true   | true
    }

    void "null marked array members keep their inferred nonnull annotations"() {
        expect:
        buildClassElement('''
package test;
@org.jspecify.annotations.NullMarked
class Test {
    String[] field;
    String[] method(String[] parameter) { return null; }
}
''') { ClassElement element ->
            def field = element.fields.find { it.name == 'field' }
            def method = element.methods.find { it.name == 'method' }
            [field, method, method.parameters[0], field.type, field.genericType,
             method.returnType, method.genericReturnType].each {
                assert it.nonNull
            }
            element
        }
    }

    void "array dimensions supplied by a generic argument retain their annotations"() {
        expect:
        buildClassElement('''
package test;
import java.lang.annotation.*;
class Test extends Parent<@Mark("leaf") String @Mark("argument") []> {}
class Parent<T> { T @Mark("field") [] field; }
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Mark { String value(); }
''') { ClassElement element ->
            def type = element.fields.find { it.name == 'field' }.genericType
            assert annotations(type) == ['field', 'argument', 'leaf']
            element
        }
    }

    private static List<String> annotations(ClassElement type) {
        def result = []
        while (true) {
            result.add(type.typeAnnotationMetadata.stringValue('test.Mark').orElse('-'))
            if (!type.array) {
                return result
            }
            type = type.fromArray()
        }
    }
}

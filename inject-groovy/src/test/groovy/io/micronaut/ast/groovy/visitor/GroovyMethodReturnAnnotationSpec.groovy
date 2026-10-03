package io.micronaut.ast.groovy.visitor

import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.AnnotationElement
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.PrimitiveElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class GroovyMethodReturnAnnotationSpec extends AbstractBeanDefinitionSpec {

    private static final String SOURCE = '''
package test

import java.lang.annotation.*

class Subject extends Base<String> {

    @ReturnTag("return")
    @MethodOnly
    @NoTarget
    String tagged() { return "" }

    String plain() { return "" }

    int plainInt() { return 0 }

    @ReturnTag("return")
    Problem sameType() throws Problem { return null }

    @ReturnTag("a")
    @ReturnTag("b")
    String repeated() { return "" }

    @ReturnTag("shape")
    int primitive() { return 0 }

    @ReturnTag("shape")
    Kind enumeration() { return Kind.ONE }

    @ReturnTag("shape")
    Marker annotation() { return null }

    @ReturnTag("shape")
    String[] array() { return null }

    @ReturnTag("shape")
    int[] primitiveArray() { return null }
}

class Base<T extends CharSequence> {

    @ReturnTag("root")
    T root(T value) { return null }

    @ReturnTag("outer")
    List<T> nested() { return null }
}

class Problem extends Exception {}

enum Kind { ONE }

@Retention(RetentionPolicy.RUNTIME)
@interface Marker {}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE_USE])
@Repeatable(ReturnTags)
@interface ReturnTag {
    String value()
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE_USE])
@interface ReturnTags {
    ReturnTag[] value()
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface MethodOnly {}

@Retention(RetentionPolicy.RUNTIME)
@interface NoTarget {}
'''

    private static List<String> tagValues(AnnotationMetadata metadata) {
        return List.copyOf(metadata.getAnnotationValuesByName('test.ReturnTag')*.stringValue()*.orElse(null))
    }

    void setup() {
        ReturnAnnotationVisitor.reset()
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, ReturnAnnotationVisitor.name)
    }

    void cleanup() {
        ReturnAnnotationVisitor.reset()
    }

    private Map captureClass(Closure<Map> reader) {
        ReturnAnnotationVisitor.reader = reader
        buildClassLoader(SOURCE)
        assert ReturnAnnotationVisitor.captured != null: 'The visitor must observe test.Subject during compilation'
        return ReturnAnnotationVisitor.captured
    }

    private static Map typeSnapshot(ClassElement type) {
        return Map.copyOf([
            name: type.name,
            primitive: type.isPrimitive(),
            enumeration: type.isEnum(),
            annotation: type instanceof AnnotationElement,
            dimensions: type.getArrayDimensions(),
            tags: tagValues(type.getTypeAnnotationMetadata())
        ])
    }

    void "only annotations targeting TYPE_USE reach a method return"() {
        given:
        def snapshot = captureClass { ClassElement subject ->
            def method = subject.getMethods().find { it.name == 'tagged' }
            def plain = subject.getMethods().find { it.name == 'plain' }
            def methodMetadata = method.getMethodAnnotationMetadata()
            def methodTags = tagValues(methodMetadata)
            def methodNames = Set.copyOf(methodMetadata.getAnnotationNames())
            def rawMetadata = method.getReturnType().getTypeAnnotationMetadata()
            def rawTags = tagValues(rawMetadata)
            def rawNames = Set.copyOf(rawMetadata.getAnnotationNames())
            def genericMetadata = method.getGenericReturnType().getTypeAnnotationMetadata()
            return [
                methodTags: methodTags, methodNames: methodNames,
                rawTags: rawTags, rawNames: rawNames,
                genericTags: tagValues(genericMetadata), genericNames: Set.copyOf(genericMetadata.getAnnotationNames()),
                plainRawTags: tagValues(plain.getReturnType().getTypeAnnotationMetadata()),
                plainGenericTags: tagValues(plain.getGenericReturnType().getTypeAnnotationMetadata())
            ]
        }

        expect:
        snapshot.methodTags == ['return']
        snapshot.methodNames.contains('test.MethodOnly')
        snapshot.methodNames.contains('test.NoTarget')

        and:
        snapshot.rawTags == ['return']
        snapshot.genericTags == ['return']
        !snapshot.rawNames.contains('test.MethodOnly')
        !snapshot.rawNames.contains('test.NoTarget')
        !snapshot.genericNames.contains('test.MethodOnly')
        !snapshot.genericNames.contains('test.NoTarget')
        snapshot.plainRawTags == []
        snapshot.plainGenericTags == []
    }

    void "a method annotation on a reference placeholder stays at the return root"() {
        given:
        def snapshot = captureClass { ClassElement subject ->
            def root = subject.getMethods().find { it.name == 'root' }
            def nested = subject.getMethods().find { it.name == 'nested' }
            def placeholder = root.getGenericReturnType()
            def inner = nested.getGenericReturnType().getTypeArguments().get('E')
            return [
                bindingName: root.getDeclaringType().getTypeArguments().get('T').name,
                placeholder: placeholder instanceof GenericPlaceholderElement,
                placeholderName: placeholder.name, resolvedName: placeholder.getResolved().get().name,
                innerName: inner.name,
                rootRawTags: tagValues(root.getReturnType().getTypeAnnotationMetadata()),
                rootGenericTags: tagValues(placeholder.getTypeAnnotationMetadata()),
                nestedRawTags: tagValues(nested.getReturnType().getTypeAnnotationMetadata()),
                nestedGenericTags: tagValues(nested.getGenericReturnType().getTypeAnnotationMetadata()),
                boundTags: List.copyOf(placeholder.getBounds().collect { tagValues(it.getTypeAnnotationMetadata()) }),
                resolvedTags: tagValues(placeholder.getResolved().get().getTypeAnnotationMetadata()),
                parameterTags: tagValues(root.getParameters()[0].getGenericType().getTypeAnnotationMetadata()),
                innerTags: tagValues(inner.getTypeAnnotationMetadata())
            ]
        }

        expect: "the inherited type variable is actually bound to a reference type"
        snapshot.bindingName == 'java.lang.String'
        snapshot.placeholder
        snapshot.placeholderName == 'java.lang.String'
        snapshot.resolvedName == 'java.lang.String'
        snapshot.innerName == 'java.lang.String'

        and:
        snapshot.rootRawTags == ['root']
        snapshot.rootGenericTags == ['root']
        snapshot.nestedRawTags == ['outer']
        snapshot.nestedGenericTags == ['outer']

        and: "bounds, the resolved class, parameters and nested arguments keep their own metadata"
        snapshot.boundTags.every { !it.contains('root') }
        !snapshot.resolvedTags.contains('root')
        !snapshot.parameterTags.contains('root')
        !snapshot.innerTags.contains('outer')
        !snapshot.innerTags.contains('root')
    }

    void "return and throws metadata are independent when return is read first: #returnFirst"() {
        given: "each order starts with freshly compiled elements"
        def snapshot = captureClass { ClassElement subject ->
            def method = subject.getMethods().find { it.name == 'sameType' }
            // Force metadata evaluation before accessing the other position.
            def first = returnFirst
                ? tagValues(method.getReturnType().getTypeAnnotationMetadata())
                : tagValues(method.getThrownTypes()[0].getTypeAnnotationMetadata())
            def second = returnFirst
                ? tagValues(method.getThrownTypes()[0].getTypeAnnotationMetadata())
                : tagValues(method.getReturnType().getTypeAnnotationMetadata())
            return [
                first: first, second: second,
                returnName: method.getReturnType().name, thrownName: method.getThrownTypes()[0].name,
                rawTags: tagValues(method.getReturnType().getTypeAnnotationMetadata()),
                genericTags: tagValues(method.getGenericReturnType().getTypeAnnotationMetadata()),
                thrownTags: tagValues(method.getThrownTypes()[0].getTypeAnnotationMetadata())
            ]
        }

        expect:
        snapshot.first == (returnFirst ? ['return'] : [])
        snapshot.second == (returnFirst ? [] : ['return'])
        snapshot.returnName == 'test.Problem'
        snapshot.thrownName == 'test.Problem'
        snapshot.rawTags == ['return']
        snapshot.genericTags == ['return']
        snapshot.thrownTags == []

        where:
        returnFirst << [true, false]
    }

    void "repeatable return annotations retain their declaration order without changing native nodes"() {
        given:
        def snapshot = captureClass { ClassElement subject ->
            def method = subject.getMethods().find { it.name == 'repeated' }
            def methodNode = method.getNativeType().annotatedNode()
            def methodAnnotations = methodNode.getAnnotations().toList()
            def returnAnnotations = methodNode.getReturnType().getTypeAnnotations().toList()
            def methodTags = tagValues(method.getMethodAnnotationMetadata())
            def rawTags = tagValues(method.getReturnType().getTypeAnnotationMetadata())
            def genericTags = tagValues(method.getGenericReturnType().getTypeAnnotationMetadata())
            return [
                methodTags: methodTags, rawTags: rawTags, genericTags: genericTags,
                methodNodesUnchanged: methodNode.getAnnotations() == methodAnnotations,
                returnNodesUnchanged: methodNode.getReturnType().getTypeAnnotations() == returnAnnotations
            ]
        }

        expect:
        snapshot.methodTags == ['a', 'b']
        snapshot.rawTags == ['a', 'b']
        snapshot.genericTags == ['a', 'b']
        snapshot.methodNodesUnchanged
        snapshot.returnNodesUnchanged
    }

    void "a dual-target annotation reaches both return accessors for #methodName"() {
        given:
        def snapshot = captureClass { ClassElement subject ->
            def method = subject.getMethods().find { it.name == methodName }
            def plain = subject.getMethods().find { it.name == 'plainInt' }
            return [
                returnTypes: List.copyOf([typeSnapshot(method.getReturnType()), typeSnapshot(method.getGenericReturnType())]),
                plainRawShared: plain.getReturnType().is(PrimitiveElement.INT),
                plainGenericShared: plain.getGenericReturnType().is(PrimitiveElement.INT),
                sharedAnnotationsEmpty: PrimitiveElement.INT.getAnnotationMetadata().isEmpty(),
                sharedTypeAnnotationsEmpty: PrimitiveElement.INT.getTypeAnnotationMetadata().isEmpty()
            ]
        }

        expect:
        snapshot.returnTypes*.name == [typeName, typeName]
        snapshot.returnTypes*.primitive == [primitive, primitive]
        snapshot.returnTypes*.enumeration == [enumeration, enumeration]
        snapshot.returnTypes*.annotation == [annotation, annotation]
        snapshot.returnTypes*.dimensions == [dimensions, dimensions]
        snapshot.returnTypes*.tags == [['shape'], ['shape']]

        and: "annotated primitive uses do not change the shared constant or a plain sibling"
        snapshot.plainRawShared
        snapshot.plainGenericShared
        snapshot.sharedAnnotationsEmpty
        snapshot.sharedTypeAnnotationsEmpty

        where:
        methodName       | typeName           | primitive | enumeration | annotation | dimensions
        'primitive'      | 'int'              | true      | false       | false      | 0
        'enumeration'    | 'test.Kind'        | false     | true        | false      | 0
        'annotation'     | 'test.Marker'      | false     | false       | true       | 0
        'array'          | 'java.lang.String' | false     | false       | false      | 1
        'primitiveArray' | 'int'              | true      | false       | false      | 1
    }

    static class ReturnAnnotationVisitor implements TypeElementVisitor<Object, Object> {

        static Closure<Map> reader
        static Map captured

        static void reset() {
            reader = null
            captured = null
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == 'test.Subject' && reader != null) {
                // Copy values during CANONICALIZATION; never retain lazy elements for a later phase.
                captured = Map.copyOf(reader.call(element))
            }
        }
    }
}

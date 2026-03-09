package io.micronaut.annotation.processing.visitor;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.test.AbstractTypeElementTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of JavaReconstructionSpec.
 * Tests reconstructing type signatures from ClassElement bound generic types.
 */
class JavaReconstructionTest extends AbstractTypeElementTest {

    // 1) field type
    @ParameterizedTest(name = "field type is {0}")
    @MethodSource("fieldTypeProvider")
    void fieldType(String fieldType) {
        ClassElement element = buildClassElement("""
            package example;

            import java.util.*;

            class Test<T> {
                %s field;
            }
            """.formatted(fieldType));
        var field = element.getFields().get(0);
        assertEquals(reconstructTypeSignature(field.getGenericType()), fieldType);
    }

    static Stream<Arguments> fieldTypeProvider() {
        return Stream.of(
            "String",
            "byte[]",
            "byte[][]",
            "List<String>",
            "List<T>",
            "List<T[]>",
            "List<T[][]>",
            "List<? extends CharSequence>",
            "List<? super String>",
            "List<? extends T[]>",
            "List<? extends T[][]>",
            "List<? extends T[][][]>",
            "List<? extends List<? extends T[]>[]>",
            "List<? extends List<? extends T[]>[][]>",
            "List<? extends List<? extends T[][]>[][]>",
            "List<? extends List>",
            "List<? extends List<?>>"
        ).map(Arguments::of);
    }

    // 2) field type is wildcard extending byte[]
    @Test
    void fieldTypeWildcardExtendingArray() {
        ClassElement element = buildClassElement("""
            package example;

            import java.util.*;

            class Test<T> {
                List<? extends byte[]> field;
            }
            """);
        var field = element.getFields().get(0);
        // Wildcards with arrays not supported yet; expect List<byte[]>
        assertEquals("List<byte[]>", reconstructTypeSignature(field.getGenericType()));
    }

    // 3) super type
    @ParameterizedTest(name = "super type is {0}")
    @MethodSource("superTypeProvider")
    void superType(String superType) {
        ClassElement element = buildClassElement("""
            package example;

            import java.util.*;

            abstract class Test<T> extends %s {
            }
            """.formatted(superType));
        assertEquals(superType, reconstructTypeSignature(element.getSuperType().get()));
    }

    static Stream<Arguments> superTypeProvider() {
        return Stream.of(
            "AbstractList",
            "AbstractList<String>",
            "AbstractList<T>",
            "AbstractList<T[]>",
            "AbstractList<List<? extends CharSequence>>",
            "AbstractList<List<? super String>>",
            "AbstractList<List<? extends T[]>>",
            "AbstractList<List<? extends T[]>[]>",
            "AbstractList<List>",
            "AbstractList<List<?>>"
        ).map(Arguments::of);
    }

    // 4) super interface
    @ParameterizedTest(name = "super interface is {0}")
    @MethodSource("superInterfaceProvider")
    void superInterface(String superType) {
        ClassElement element = buildClassElement("""
            package example;

            import java.util.*;

            abstract class Test<T> implements %s {
            }
            """.formatted(superType));
        assertEquals(superType, reconstructTypeSignature(element.getInterfaces().iterator().next()));
    }

    static Stream<Arguments> superInterfaceProvider() {
        return Stream.of(
            "List",
            "List<String>",
            "List<T>",
            "List<T[]>",
            "List<List<? extends CharSequence>>",
            "List<List<? super String>>",
            "List<List<? extends T[]>>",
            "List<List<? extends T[]>[]>",
            "List<List>",
            "List<List<?>>"
        ).map(Arguments::of);
    }

    // 5) type vars declared on type
    @ParameterizedTest(name = "type var is {0}")
    @MethodSource("typeVarTypeDeclProvider")
    void typeVarsDeclaredOnType(String decl) {
        ClassElement element = buildClassElement("""
            package example;

            import java.util.*;

            abstract class Test<A, %s> {
            }
            """.formatted(decl));
        assertEquals(decl, reconstructTypeSignature(element.getDeclaredGenericPlaceholders().get(1), true));
    }

    static Stream<Arguments> typeVarTypeDeclProvider() {
        return Stream.of(
            "T",
            "T extends CharSequence",
            "T extends A",
            "T extends List",
            "T extends List<?>",
            "T extends List<T>",
            "T extends List<? extends T>",
            "T extends List<? extends A>",
            "T extends List<T[]>"
        ).map(Arguments::of);
    }

    // 6) type vars declared on method
    @ParameterizedTest(name = "type var is {0}")
    @MethodSource("typeVarMethodDeclProvider")
    void typeVarsDeclaredOnMethod(String decl) {
        ClassElement element = buildClassElement("""
            package example;

            import java.util.*;

            abstract class Test<A> {
                <%s> void method() {}
            }
            """.formatted(decl));
        MethodElement method = element.getEnclosedElement(
            ElementQuery.ALL_METHODS.named(s -> s.equals("method"))
        ).get();
        assertEquals(decl, reconstructTypeSignature(method.getDeclaredTypeVariables().get(0), true));
    }

    static Stream<Arguments> typeVarMethodDeclProvider() {
        return Stream.of(
            "T",
            "T extends CharSequence",
            "T extends A",
            "T extends List",
            "T extends List<?>",
            "T extends List<T>",
            "T extends List<? extends T>",
            "T extends List<? extends A>",
            "T extends List<T[]>"
        ).map(Arguments::of);
    }

    // 7) bound field type
    @ParameterizedTest(name = "bound field type is {0}")
    @MethodSource("boundFieldTypeProvider")
    void boundFieldType(String fieldType, String expectedType) {
        ClassElement element = buildClassElement("""
            package example;

            import java.util.*;

            class Wrapper {
                Test<String> test;
            }
            class Test<T> {
                %s field;
            }
            """.formatted(fieldType));
        var field = element.getFields().get(0);
        var innerField = field.getGenericType().getFields().get(0);
        assertEquals(expectedType, reconstructTypeSignature(innerField.getGenericType()));
    }

    static Stream<Arguments> boundFieldTypeProvider() {
        return Stream.of(
            Arguments.of("String", "String"),
            Arguments.of("List<String>", "List<String>"),
            Arguments.of("List<T>", "List<String>"),
            Arguments.of("List<T[]>", "List<String[]>"),
            Arguments.of("List<? extends CharSequence>", "List<? extends CharSequence>"),
            Arguments.of("List<? super String>", "List<? super String>"),
            Arguments.of("List<? extends T[]>", "List<? extends String[]>"),
            Arguments.of("List<? extends List<? extends T[]>[]>", "List<? extends List<? extends String[]>[]>"),
            Arguments.of("List<? extends List>", "List<? extends List>"),
            Arguments.of("List<? extends List<?>>", "List<? extends List<?>>")
        );
    }

    // 8) bound field type - bound variables not implemented
    @ParameterizedTest(name = "bound field type (not implemented) is {0}")
    @MethodSource("boundFieldTypeProvider")
    void boundFieldTypeBoundVariablesNotImplemented(String fieldType, String expectedType) {
        ClassElement element = buildClassElement("""
            package example;

            import java.util.*;

            class Wrapper {
                Test<String> test;
            }
            class Test<T> {
                %s field;
            }
            """.formatted(fieldType));
        var field = element.getFields().get(0);
        var innerField = field.getGenericType().getFields().get(0);
        assertEquals(expectedType, reconstructTypeSignature(innerField.getGenericType()));
    }

    // 9) bound field type to other variable
    @ParameterizedTest(name = "bound field type to other var is {0}")
    @MethodSource("boundFieldTypeOtherVarProvider")
    void boundFieldTypeToOtherVariable(String fieldType, String expectedType) {
        ClassElement element = buildClassElement("""
            package example;

            import java.util.*;

            class Wrapper<U> {
                Test<U> test;
            }
            class Test<T> {
                %s field;
            }
            """.formatted(fieldType));
        var field = element.getFields().get(0);
        var innerField = field.getGenericType().getFields().get(0);
        assertEquals(expectedType, reconstructTypeSignature(innerField.getGenericType()));
    }

    static Stream<Arguments> boundFieldTypeOtherVarProvider() {
        return Stream.of(
            Arguments.of("String", "String"),
            Arguments.of("List<String>", "List<String>"),
            Arguments.of("List<T>", "List<T>"),
            Arguments.of("List<T[]>", "List<T[]>"),
            Arguments.of("List<? extends CharSequence>", "List<? extends CharSequence>"),
            Arguments.of("List<? super String>", "List<? super String>"),
            Arguments.of("List<? extends T[]>", "List<? extends T[]>"),
            Arguments.of("List<? extends List<? extends T[]>[]>", "List<? extends List<? extends T[]>[]>"),
            Arguments.of("List<? extends List>", "List<? extends List>"),
            Arguments.of("List<? extends List<?>>", "List<? extends List<?>>")
        );
    }

    // 10) bound field type to other variable - bound variables not implemented
    @ParameterizedTest(name = "bound field type to other var (not implemented) is {0}")
    @MethodSource("boundFieldTypeOtherVarProvider")
    void boundFieldTypeToOtherVariableBoundVariablesNotImplemented(String fieldType, String expectedType) {
        ClassElement element = buildClassElement("""
            package example;

            import java.util.*;

            class Wrapper<U> {
                Test<U> test;
            }
            class Test<T> {
                %s field;
            }
            """.formatted(fieldType));
        var field = element.getFields().get(0);
        var innerField = field.getGenericType().getFields().get(0);
        assertEquals(expectedType, reconstructTypeSignature(innerField.getGenericType()));
    }

    // 11) unbound super type (check both class and interface super)
    @ParameterizedTest(name = "unbound super type decl={0}, params={1}")
    @MethodSource("unboundSuperTypeProvider")
    void unboundSuperType(String decl, String params, String expected) {
        ClassElement superElement = buildClassElement("""
            package example;

            import java.util.*;

            class Sub<U> extends Sup<%s> {
            }
            class Sup<%s> {
            }
            """.formatted(params, decl));
        ClassElement interfaceElement = buildClassElement("""
            package example;

            import java.util.*;

            class Sub<U> implements Sup<%s> {
            }
            interface Sup<%s> {
            }
            """.formatted(params, decl));
        assertEquals(expected, reconstructTypeSignature(superElement.getSuperType().get()));
        assertEquals(expected, reconstructTypeSignature(interfaceElement.getInterfaces().iterator().next()));
    }

    static Stream<Arguments> unboundSuperTypeProvider() {
        return Stream.of(
            Arguments.of("T", "String", "Sup<String>"),
            Arguments.of("T", "List<U>", "Sup<List<U>>"),
            Arguments.of("T", "List<? extends U>", "Sup<List<? extends U>>"),
            Arguments.of("T", "List<? super U>", "Sup<List<? super U>>")
        );
    }

    // 12) bound super type
    @ParameterizedTest(name = "bound super type decl={0}, params={1}")
    @MethodSource("boundSuperTypeProvider")
    void boundSuperType(String decl, String params, String expected) {
        ClassElement superElement = buildClassElement("""
            package example;

            import java.util.*;

            class Sub<U> extends Sup<%s> {
            }
            class Sup<%s> {
            }
            """.formatted(params, decl)).withTypeArguments(List.of(ClassElement.of(String.class)));
        ClassElement interfaceElement = buildClassElement("""
            package example;

            import java.util.*;

            class Sub<U> implements Sup<%s> {
            }
            interface Sup<%s> {
            }
            """.formatted(params, decl)).withTypeArguments(List.of(ClassElement.of(String.class)));
        assertEquals(expected, reconstructTypeSignature(superElement.getSuperType().get()));
        assertEquals(expected, reconstructTypeSignature(interfaceElement.getInterfaces().iterator().next()));
    }

    static Stream<Arguments> boundSuperTypeProvider() {
        return Stream.of(
            Arguments.of("T", "String", "Sup<String>"),
            Arguments.of("T", "List<U>", "Sup<List<String>>"),
            Arguments.of("T", "List<? extends U>", "Sup<List<? extends String>>"),
            Arguments.of("T", "List<? super U>", "Sup<List<? super String>>")
        );
    }

    // 13) bound super type - binding not implemented (same expectations as above)
    @ParameterizedTest(name = "bound super type (not implemented) decl={0}, params={1}")
    @MethodSource("boundSuperTypeProvider")
    void boundSuperTypeBindingNotImplemented(String decl, String params, String expected) {
        ClassElement superElement = buildClassElement("""
            package example;

            import java.util.*;

            class Sub<U> extends Sup<%s> {
            }
            class Sup<%s> {
            }
            """.formatted(params, decl)).withTypeArguments(List.of(ClassElement.of(String.class)));
        ClassElement interfaceElement = buildClassElement("""
            package example;

            import java.util.*;

            class Sub<U> implements Sup<%s> {
            }
            interface Sup<%s> {
            }
            """.formatted(params, decl)).withTypeArguments(List.of(ClassElement.of(String.class)));
        assertEquals(expected, reconstructTypeSignature(superElement.getSuperType().get()));
        assertEquals(expected, reconstructTypeSignature(interfaceElement.getInterfaces().iterator().next()));
    }

    // 14) fold type variable
    @ParameterizedTest(name = "fold declaration {0}")
    @MethodSource("foldTypeVarProvider")
    void foldTypeVariable(String decl, String expected) {
        ClassElement classElement = buildClassElement("""
            package example;

            import java.util.*;

            class Test<T> {
                %s field;
            }
            """.formatted(decl));
        ClassElement fieldType = classElement.getFields().get(0).getType();
        String folded = reconstructTypeSignature(fieldType.foldBoundGenericTypes(ce -> {
            if (ce != null && ce.isGenericPlaceholder() && ((GenericPlaceholderElement) ce).getVariableName().equals("T")) {
                return ClassElement.of(String.class);
            }
            return ce;
        }));
        assertEquals(expected, folded);
    }

    static Stream<Arguments> foldTypeVarProvider() {
        return Stream.of(
            Arguments.of("String", "String"),
            Arguments.of("T", "String"),
            Arguments.of("List<T>", "List<String>"),
            Arguments.of("Map<Object, T>", "Map<Object, String>"),
            Arguments.of("List<? extends T>", "List<? extends String>"),
            Arguments.of("List<? super T>", "List<? super String>")
        );
    }

    // 15) fold type variable to null
    @ParameterizedTest(name = "fold to null declaration {0}")
    @MethodSource("foldTypeVarToNullProvider")
    void foldTypeVariableToNull(String decl, String expected) {
        ClassElement classElement = buildClassElement("""
            package example;

            import java.util.*;

            class Test<T> {
                %s field;
            }
            """.formatted(decl));
        ClassElement fieldType = classElement.getFields().get(0).getType();
        String folded = reconstructTypeSignature(fieldType.foldBoundGenericTypes(ce -> {
            if (ce != null && ce.isGenericPlaceholder() && ((GenericPlaceholderElement) ce).getVariableName().equals("T")) {
                return null;
            }
            return ce;
        }));
        assertEquals(expected, folded);
    }

    static Stream<Arguments> foldTypeVarToNullProvider() {
        return Stream.of(
            Arguments.of("String", "String"),
            Arguments.of("List<T>", "List"),
            Arguments.of("Map<Object, T>", "Map"),
            Arguments.of("List<? extends T>", "List"),
            Arguments.of("List<? super T>", "List")
        );
    }

    // 16) distinguish list types
    @Test
    void distinguishListTypes() {
        ClassElement classElement = buildClassElement("""
            package example;

            import java.util.*;

            class Test {
                List field1;
                List<?> field2;
                List<Object> field3;
            }
            """);
        ClassElement rawType = classElement.getFields().get(0).getGenericType();
        ClassElement wildcardType = classElement.getFields().get(1).getGenericType();
        ClassElement objectType = classElement.getFields().get(2).getGenericType();

        assertTrue(rawType.getBoundGenericTypes().isEmpty());
        assertEquals("java.lang.Object", rawType.getTypeArguments().get("E").getType().getName());
        assertTrue(rawType.getTypeArguments().get("E").isRawType());
        assertFalse(rawType.getTypeArguments().get("E").isWildcard());
        assertTrue(rawType.getTypeArguments().get("E").isGenericPlaceholder());

        assertEquals(1, wildcardType.getBoundGenericTypes().size());
        assertTrue(wildcardType.getBoundGenericTypes().get(0).isWildcard());
        assertEquals("java.lang.Object", wildcardType.getTypeArguments().get("E").getType().getName());
        assertTrue(wildcardType.getTypeArguments().get("E").isWildcard());
        assertFalse(wildcardType.getTypeArguments().get("E").isRawType());

        assertEquals(1, objectType.getBoundGenericTypes().size());
        assertFalse(objectType.getBoundGenericTypes().get(0).isWildcard());
        assertEquals("java.lang.Object", objectType.getTypeArguments().get("E").getType().getName());
        assertFalse(objectType.getTypeArguments().get("E").isWildcard());
        assertFalse(objectType.getTypeArguments().get("E").isRawType());
        assertFalse(objectType.getTypeArguments().get("E").isGenericPlaceholder());
    }

    // 17) distinguish list types 2
    @Test
    void distinguishListTypes2() {
        ClassElement classElement = buildClassElement("""
            package example;

            import java.util.*;
            import java.lang.Number;

            class Test {
                List field1;
                List<?> field2;
                List<Object> field3;
                List<String> field4;
                List<? extends Number> field5;
            }
            """);
        ClassElement rawType = classElement.getFields().get(0).getType();
        ClassElement wildcardType = classElement.getFields().get(1).getType();
        ClassElement objectType = classElement.getFields().get(2).getType();
        ClassElement stringType = classElement.getFields().get(3).getType();
        ClassElement numberType = classElement.getFields().get(4).getType();

        assertEquals("java.lang.Object", rawType.getTypeArguments().get("E").getType().getName());
        assertTrue(rawType.getTypeArguments().get("E").isRawType());
        assertFalse(rawType.getTypeArguments().get("E").isWildcard());
        assertTrue(rawType.getTypeArguments().get("E").isGenericPlaceholder());

        assertEquals("java.lang.Object", wildcardType.getTypeArguments().get("E").getType().getName());
        assertTrue(wildcardType.getTypeArguments().get("E").isWildcard());
        assertFalse(((WildcardElement) wildcardType.getTypeArguments().get("E")).isBounded());
        assertFalse(wildcardType.getTypeArguments().get("E").isRawType());

        assertEquals("java.lang.Object", objectType.getTypeArguments().get("E").getType().getName());
        assertFalse(objectType.getTypeArguments().get("E").isWildcard());
        assertFalse(objectType.getTypeArguments().get("E").isRawType());
        assertFalse(objectType.getTypeArguments().get("E").isGenericPlaceholder());

        assertEquals("java.lang.String", stringType.getTypeArguments().get("E").getType().getName());
        assertFalse(stringType.getTypeArguments().get("E").isWildcard());
        assertFalse(stringType.getTypeArguments().get("E").isRawType());
        assertFalse(stringType.getTypeArguments().get("E").isGenericPlaceholder());

        assertEquals("java.lang.Number", numberType.getTypeArguments().get("E").getType().getName());
        assertTrue(numberType.getTypeArguments().get("E").isWildcard());
        assertTrue(((WildcardElement) numberType.getTypeArguments().get("E")).isBounded());
        assertFalse(numberType.getTypeArguments().get("E").isRawType());
    }

    // 18) distinguish base list type
    @Test
    void distinguishBaseListType() {
        ClassElement classElement = buildClassElement("""
            package example;

            import java.util.*;
            import java.lang.Number;

            class Test extends Base<String> {
            }

            abstract class Base<T> {
                List field1;
                List<?> field2;
                List<Object> field3;
                List<T> field4;
            }

            """);
        ClassElement rawType = classElement.getFields().get(0).getType();
        ClassElement wildcardType = classElement.getFields().get(1).getType();
        ClassElement objectType = classElement.getFields().get(2).getType();
        ClassElement genericType = classElement.getFields().get(3).getType();

        assertEquals("java.lang.Object", rawType.getTypeArguments().get("E").getType().getName());
        assertTrue(rawType.getTypeArguments().get("E").isRawType());
        assertFalse(rawType.getTypeArguments().get("E").isWildcard());
        assertTrue(rawType.getTypeArguments().get("E").isGenericPlaceholder());

        assertEquals("java.lang.Object", wildcardType.getTypeArguments().get("E").getType().getName());
        assertTrue(wildcardType.getTypeArguments().get("E").isWildcard());
        assertFalse(((WildcardElement) wildcardType.getTypeArguments().get("E")).isBounded());
        assertFalse(wildcardType.getTypeArguments().get("E").isRawType());

        assertEquals("java.lang.Object", objectType.getTypeArguments().get("E").getType().getName());
        assertFalse(objectType.getTypeArguments().get("E").isWildcard());
        assertFalse(objectType.getTypeArguments().get("E").isRawType());
        assertFalse(objectType.getTypeArguments().get("E").isGenericPlaceholder());

        assertEquals("java.lang.Object", genericType.getTypeArguments().get("E").getType().getName());
        assertFalse(genericType.getTypeArguments().get("E").isWildcard());
        assertFalse(genericType.getTypeArguments().get("E").isRawType());
        assertTrue(genericType.getTypeArguments().get("E").isGenericPlaceholder());
        assertTrue(((GenericPlaceholderElement) genericType.getTypeArguments().get("E")).getResolved().isEmpty());
    }

    // 19) distinguish base list generic type
    @Test
    void distinguishBaseListGenericType() {
        ClassElement classElement = buildClassElement("""
            package example;

            import java.util.*;
            import java.lang.Number;

            class Test extends Base<String> {
            }

            abstract class Base<T> {
                List field1;
                List<?> field2;
                List<Object> field3;
                List<T> field4;
            }

            """);
        ClassElement rawType = classElement.getFields().get(0).getGenericType();
        ClassElement wildcardType = classElement.getFields().get(1).getGenericType();
        ClassElement objectType = classElement.getFields().get(2).getGenericType();
        ClassElement genericType = classElement.getFields().get(3).getGenericType();

        assertEquals("java.lang.Object", rawType.getTypeArguments().get("E").getType().getName());
        assertTrue(rawType.getTypeArguments().get("E").isRawType());
        assertFalse(rawType.getTypeArguments().get("E").isWildcard());
        assertTrue(rawType.getTypeArguments().get("E").isGenericPlaceholder());

        assertEquals("java.lang.Object", wildcardType.getTypeArguments().get("E").getType().getName());
        assertTrue(wildcardType.getTypeArguments().get("E").isWildcard());
        assertFalse(((WildcardElement) wildcardType.getTypeArguments().get("E")).isBounded());
        assertFalse(wildcardType.getTypeArguments().get("E").isRawType());

        assertEquals("java.lang.Object", objectType.getTypeArguments().get("E").getType().getName());
        assertFalse(objectType.getTypeArguments().get("E").isWildcard());
        assertFalse(objectType.getTypeArguments().get("E").isRawType());
        assertFalse(objectType.getTypeArguments().get("E").isGenericPlaceholder());

        assertEquals("java.lang.String", genericType.getTypeArguments().get("E").getType().getName());
        assertFalse(genericType.getTypeArguments().get("E").isWildcard());
        assertFalse(genericType.getTypeArguments().get("E").isRawType());
        assertTrue(genericType.getTypeArguments().get("E").isGenericPlaceholder());
        ClassElement resolved = ((GenericPlaceholderElement) genericType.getTypeArguments().get("E")).getResolved().get();
        assertEquals("java.lang.String", resolved.getName());
        assertFalse(resolved.isWildcard());
        assertFalse(resolved.isRawType());
        assertFalse(resolved.isGenericPlaceholder());
    }
}

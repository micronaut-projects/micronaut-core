package io.micronaut.annotation.processing.visitor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.MethodElement
import spock.lang.PendingFeature
import spock.lang.Unroll

/**
 * These tests are based on a {@link #reconstructTypeSignature} method that looks at {@link ClassElement#getBoundGenericTypes()}
 * to transform a {@link ClassElement} back to its string representation. This way, we can easily check what
 * {@link ClassElement}s returned by various methods look like.
 */
class JavaReconstructionSpec extends AbstractTypeElementSpec {
    @Unroll("field type is #fieldType")
    def 'field type'() {
        given:
        def element = buildClassElement("""
package example;

import java.util.*;

class Test<T> {
    $fieldType field;
}
""")
        def field = element.getFields()[0]

        expect:
        reconstructTypeSignature(field.genericType) == fieldType

        where:
        fieldType << [
                'String',
                'List<String>',
                'List<T>',
                'List<T[]>',
                'List<? extends CharSequence>',
                'List<? super String>',
                'List<? extends T[]>',
                'List<? extends List<? extends T[]>[]>',
                'List<? extends List>',
                'List<? extends List<?>>',
        ]
    }

    @Unroll("super type is #superType")
    def 'super type'() {
        given:
        def element = buildClassElement("""
package example;

import java.util.*;

abstract class Test<T> extends $superType {
}
""")

        expect:
        reconstructTypeSignature(element.superType.get()) == superType

        where:
        superType << [
                'AbstractList',
                'AbstractList<String>',
                'AbstractList<T>',
                'AbstractList<T[]>',
                'AbstractList<List<? extends CharSequence>>',
                'AbstractList<List<? super String>>',
                'AbstractList<List<? extends T[]>>',
                'AbstractList<List<? extends T[]>[]>',
                'AbstractList<List>',
                'AbstractList<List<?>>',
        ]
    }

    @Unroll("super interface is #superType")
    def 'super interface'() {
        given:
        def element = buildClassElement("""
package example;

import java.util.*;

abstract class Test<T> implements $superType {
}
""")

        expect:
        reconstructTypeSignature(element.interfaces[0]) == superType

        where:
        superType << [
                'List',
                'List<String>',
                'List<T>',
                'List<T[]>',
                'List<List<? extends CharSequence>>',
                'List<List<? super String>>',
                'List<List<? extends T[]>>',
                'List<List<? extends T[]>[]>',
                'List<List>',
                'List<List<?>>',
        ]
    }

    @Unroll("type var is #decl")
    def 'type vars declared on type'() {
        given:
        def element = buildClassElement("""
package example;

import java.util.*;

abstract class Test<A, $decl> {
}
""")

        expect:
        reconstructTypeSignature(element.declaredGenericPlaceholders[1], true) == decl

        where:
        decl << [
                'T',
                'T extends CharSequence',
                'T extends A',
                'T extends List',
                'T extends List<?>',
                'T extends List<T>',
                'T extends List<? extends T>',
                'T extends List<? extends A>',
                'T extends List<T[]>',
        ]
    }

    @Unroll("type var is #decl")
    def 'type vars declared on method'() {
        given:
        def element = buildClassElement("""
package example;

import java.util.*;

abstract class Test<A> {
    <$decl> void method() {}
}
""")
        def method = element.<MethodElement> getEnclosedElement(ElementQuery.ALL_METHODS.named(s -> s == 'method')).get()

        expect:
        reconstructTypeSignature(method.declaredTypeVariables[0], true) == decl

        where:
        decl << [
                'T',
                'T extends CharSequence',
                'T extends A',
                'T extends List',
                'T extends List<?>',
                'T extends List<T>',
                'T extends List<? extends T>',
                'T extends List<? extends A>',
                'T extends List<T[]>',
        ]
    }

    @PendingFeature
    @Unroll("field type is #fieldType")
    def 'bound field type'() {
        given:
        def element = buildClassElement("""
package example;

import java.util.*;

class Wrapper {
    Test<String> test;
}
class Test<T> {
    $fieldType field;
}
""")
        def field = element.getFields()[0].genericType.getFields()[0]

        expect:
        reconstructTypeSignature(field.genericType) == expectedType

        where:
        fieldType                               | expectedType
        'String'                                | 'String'
        'List<String>'                          | 'List<String>'
        'List<T>'                               | 'List<String>'
        'List<T[]>'                             | 'List<String[]>'
        'List<? extends CharSequence>'          | 'List<? extends CharSequence>'
        'List<? super String>'                  | 'List<? super String>'
        'List<? extends T[]>'                   | 'List<? extends String[]>'
        'List<? extends List<? extends T[]>[]>' | 'List<? extends List<? extends String[]>[]>'
        'List<? extends List>'                  | 'List<? extends List>'
        'List<? extends List<?>>'               | 'List<? extends List<?>>'
    }

    @Unroll("field type is #fieldType")
    def 'bound field type - bound variables not implemented'() {
        given:
        def element = buildClassElement("""
package example;

import java.util.*;

class Wrapper {
    Test<String> test;
}
class Test<T> {
    $fieldType field;
}
""")
        def field = element.getFields()[0].genericType.getFields()[0]

        expect:
        reconstructTypeSignature(field.genericType) == expectedType

        where:
        fieldType                               | expectedType
        'String'                                | 'String'
        'List<String>'                          | 'List<String>'
        'List<T>'                               | 'List<T>'
        'List<T[]>'                             | 'List<T[]>'
        'List<? extends CharSequence>'          | 'List<? extends CharSequence>'
        'List<? super String>'                  | 'List<? super String>'
        'List<? extends T[]>'                   | 'List<? extends T[]>'
        'List<? extends List<? extends T[]>[]>' | 'List<? extends List<? extends T[]>[]>'
        'List<? extends List>'                  | 'List<? extends List>'
        'List<? extends List<?>>'               | 'List<? extends List<?>>'
    }

    @PendingFeature
    @Unroll("field type is #fieldType")
    def 'bound field type to other variable'() {
        given:
        def element = buildClassElement("""
package example;

import java.util.*;

class Wrapper<U> {
    Test<U> test;
}
class Test<T> {
    $fieldType field;
}
""")
        def field = element.getFields()[0].genericType.getFields()[0]

        expect:
        reconstructTypeSignature(field.genericType) == expectedType

        where:
        fieldType                               | expectedType
        'String'                                | 'String'
        'List<String>'                          | 'List<String>'
        'List<T>'                               | 'List<U>'
        'List<T[]>'                             | 'List<U[]>'
        'List<? extends CharSequence>'          | 'List<? extends CharSequence>'
        'List<? super String>'                  | 'List<? super String>'
        'List<? extends T[]>'                   | 'List<? extends U[]>'
        'List<? extends List<? extends T[]>[]>' | 'List<? extends List<? extends U[]>[]>'
        'List<? extends List>'                  | 'List<? extends List>'
        'List<? extends List<?>>'               | 'List<? extends List<?>>'
    }

    @Unroll("field type is #fieldType")
    def 'bound field type to other variable - bound variables not implemented'() {
        given:
        def element = buildClassElement("""
package example;

import java.util.*;

class Wrapper<U> {
    Test<U> test;
}
class Test<T> {
    $fieldType field;
}
""")
        def field = element.getFields()[0].genericType.getFields()[0]

        expect:
        reconstructTypeSignature(field.genericType) == expectedType

        where:
        fieldType                               | expectedType
        'String'                                | 'String'
        'List<String>'                          | 'List<String>'
        'List<T>'                               | 'List<T>'
        'List<T[]>'                             | 'List<T[]>'
        'List<? extends CharSequence>'          | 'List<? extends CharSequence>'
        'List<? super String>'                  | 'List<? super String>'
        'List<? extends T[]>'                   | 'List<? extends T[]>'
        'List<? extends List<? extends T[]>[]>' | 'List<? extends List<? extends T[]>[]>'
        'List<? extends List>'                  | 'List<? extends List>'
        'List<? extends List<?>>'               | 'List<? extends List<?>>'
    }

    def 'unbound super type'() {
        given:
        def superElement = buildClassElement("""
package example;

import java.util.*;

class Sub<U> extends Sup<$params> {
}
class Sup<$decl> {
}
""")
        def interfaceElement = buildClassElement("""
package example;

import java.util.*;

class Sub<U> implements Sup<$params> {
}
interface Sup<$decl> {
}
""")

        expect:
        reconstructTypeSignature(superElement.getSuperType().get()) == expected
        reconstructTypeSignature(interfaceElement.getInterfaces()[0]) == expected

        where:
        decl | params              | expected
        'T'  | 'String'            | 'Sup<String>'
        'T'  | 'List<U>'           | 'Sup<List<U>>'
        'T'  | 'List<? extends U>' | 'Sup<List<? extends U>>'
        'T'  | 'List<? super U>'   | 'Sup<List<? super U>>'
    }

    @PendingFeature
    def 'bound super type'() {
        given:
        def superElement = buildClassElement("""
package example;

import java.util.*;

class Sub<U> extends Sup<$params> {
}
class Sup<$decl> {
}
""").withBoundGenericTypes([ClassElement.of(String)])
        def interfaceElement = buildClassElement("""
package example;

import java.util.*;

class Sub<U> implements Sup<$params> {
}
interface Sup<$decl> {
}
""").withBoundGenericTypes([ClassElement.of(String)])

        expect:
        reconstructTypeSignature(superElement.getSuperType().get()) == expected
        reconstructTypeSignature(interfaceElement.getInterfaces()[0]) == expected

        where:
        decl | params              | expected
        'T'  | 'String'            | 'Sup<String>'
        'T'  | 'List<U>'           | 'Sup<List<String>>'
        'T'  | 'List<? extends U>' | 'Sup<List<? extends String>>'
        'T'  | 'List<? super U>'   | 'Sup<List<? super String>>'
    }

    def 'bound super type - binding not implemented'() {
        given:
        def superElement = buildClassElement("""
package example;

import java.util.*;

class Sub<U> extends Sup<$params> {
}
class Sup<$decl> {
}
""").withBoundGenericTypes([ClassElement.of(String)])
        def interfaceElement = buildClassElement("""
package example;

import java.util.*;

class Sub<U> implements Sup<$params> {
}
interface Sup<$decl> {
}
""").withBoundGenericTypes([ClassElement.of(String)])

        expect:
        reconstructTypeSignature(superElement.getSuperType().get()) == expected
        reconstructTypeSignature(interfaceElement.getInterfaces()[0]) == expected

        where:
        decl | params              | expected
        'T'  | 'String'            | 'Sup<String>'
        'T'  | 'List<U>'           | 'Sup<List<U>>'
        'T'  | 'List<? extends U>' | 'Sup<List<? extends U>>'
        'T'  | 'List<? super U>'   | 'Sup<List<? super U>>'
    }

    @Unroll('declaration is #decl')
    def 'fold type variable'() {
        given:
        def classElement = buildClassElement("""
package example;

import java.util.*;

class Test<T> {
    $decl field;
}
""")
        def fieldType = classElement.fields[0].type
        List<? extends GenericPlaceholderElement> placeholders = fieldType.getDeclaredGenericPlaceholders()

        expect:
        // Native types should be Element if possible
        placeholders.every { it.nativeType.class.simpleName == "TypeVariableSymbol" }
        reconstructTypeSignature(fieldType.foldBoundGenericTypes {
            if (it.isGenericPlaceholder() && ((GenericPlaceholderElement) it).variableName == 'T') {
                return ClassElement.of(String)
            } else {
                return it
            }
        }) == expected

        where:
        decl                | expected
        'String'            | 'String'
        'T'                 | 'String'
        'List<T>'           | 'List<String>'
        'Map<Object, T>'    | 'Map<Object, String>'
        'List<? extends T>' | 'List<? extends String>'
        'List<? super T>'   | 'List<? super String>'
    }

    @Unroll('declaration is #decl')
    def 'fold type variable to null'() {
        given:
        def classElement = buildClassElement("""
package example;

import java.util.*;

class Test<T> {
    $decl field;
}
""")
        def fieldType = classElement.fields[0].type

        expect:
        reconstructTypeSignature(fieldType.foldBoundGenericTypes {
            if (it != null && it.isGenericPlaceholder() && ((GenericPlaceholderElement) it).variableName == 'T') {
                return null
            } else {
                return it
            }
        }) == expected

        where:
        decl                | expected
        'String'            | 'String'
        'List<T>'           | 'List'
        'Map<Object, T>'    | 'Map'
        'List<? extends T>' | 'List'
        'List<? super T>'   | 'List'
    }

    def 'distinguish list types'() {
        given:
        def classElement = buildClassElement("""
package example;

import java.util.*;

class Test {
    List field1;
    List<?> field2;
    List<Object> field3;
}
""")
        def rawType = classElement.fields[0].genericType
        def wildcardType = classElement.fields[1].genericType
        def objectType = classElement.fields[2].genericType

        expect:
        rawType.boundGenericTypes.isEmpty()

        wildcardType.boundGenericTypes.size() == 1
        wildcardType.boundGenericTypes[0].isWildcard()
        wildcardType.boundGenericTypes[0].getNativeType().class.name == 'com.sun.tools.javac.code.Type$WildcardType'

        objectType.boundGenericTypes.size() == 1
        !objectType.boundGenericTypes[0].isWildcard()
    }
}

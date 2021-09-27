package io.micronaut.annotation.processing.visitor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.FreeTypeVariableElement
import io.micronaut.inject.ast.MethodElement
import spock.lang.PendingFeature
import spock.lang.Unroll

/**
 * These tests are based on a {@link #reconstruct} method that looks at {@link ClassElement#getBoundTypeArguments()}
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
        reconstruct(field.genericType) == fieldType

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
        reconstruct(element.superType.get()) == superType

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
        reconstruct(element.interfaces[0]) == superType

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
        reconstruct(element.declaredTypeVariables[1], true) == decl

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
        reconstruct(method.declaredTypeVariables[0], true) == decl

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
        reconstruct(field.genericType) == expectedType

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
        reconstruct(field.genericType) == expectedType

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
        reconstruct(field.genericType) == expectedType

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
        reconstruct(field.genericType) == expectedType

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
        reconstruct(superElement.getSuperType().get()) == expected
        reconstruct(interfaceElement.getInterfaces()[0]) == expected

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
""").withBoundTypeArguments([ClassElement.of(String)])
        def interfaceElement = buildClassElement("""
package example;

import java.util.*;

class Sub<U> implements Sup<$params> {
}
interface Sup<$decl> {
}
""").withBoundTypeArguments([ClassElement.of(String)])

        expect:
        reconstruct(superElement.getSuperType().get()) == expected
        reconstruct(interfaceElement.getInterfaces()[0]) == expected

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
""").withBoundTypeArguments([ClassElement.of(String)])
        def interfaceElement = buildClassElement("""
package example;

import java.util.*;

class Sub<U> implements Sup<$params> {
}
interface Sup<$decl> {
}
""").withBoundTypeArguments([ClassElement.of(String)])

        expect:
        reconstruct(superElement.getSuperType().get()) == expected
        reconstruct(interfaceElement.getInterfaces()[0]) == expected

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

        expect:
        reconstruct(fieldType.foldTypes {
            if (it.isFreeTypeVariable() && ((FreeTypeVariableElement) it).variableName == 'T') {
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
}

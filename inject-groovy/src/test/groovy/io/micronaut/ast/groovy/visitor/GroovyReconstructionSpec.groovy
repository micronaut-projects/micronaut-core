package io.micronaut.ast.groovy.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.Experimental
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.WildcardElement
import spock.lang.PendingFeature
import spock.lang.Unroll

import java.util.stream.Collectors

/**
 * These tests are based on a {@link #reconstructTypeSignature} method that looks at {@link ClassElement#getBoundGenericTypes()}
 * to transform a {@link ClassElement} back to its string representation. This way, we can easily check what
 * {@link ClassElement}s returned by various methods look like.
 */
class GroovyReconstructionSpec extends AbstractBeanDefinitionSpec {

    /**
     * Create a rough source signature of the given ClassElement, using {@link ClassElement#getBoundGenericTypes()}.
     * Can be used to test that {@link ClassElement#getBoundGenericTypes()} returns the right types in the right
     * context.
     *
     * @param classElement The class element to reconstruct
     * @param typeVarsAsDeclarations Whether type variables should be represented as declarations
     * @return a String representing the type signature.
     */
    @Experimental
    protected static String reconstructTypeSignature(ClassElement classElement, boolean typeVarsAsDeclarations = false) {
        if (classElement.isArray()) {
            return reconstructTypeSignature(classElement.fromArray()) + "[]"
        } else if (classElement.isGenericPlaceholder()) {
            def freeVar = (GenericPlaceholderElement) classElement
            def name = freeVar.variableName
            if (typeVarsAsDeclarations) {
                def bounds = freeVar.bounds
                if (reconstructTypeSignature(bounds[0]) != 'Object') {
                    name += bounds.stream().map(GroovyReconstructionSpec::reconstructTypeSignature).collect(Collectors.joining(" & ", " extends ", ""))
                }
            }
            return name
        } else if (classElement.isWildcard()) {
            def we = (WildcardElement) classElement
            if (!we.lowerBounds.isEmpty()) {
                return we.lowerBounds.stream().map(GroovyReconstructionSpec::reconstructTypeSignature).collect(Collectors.joining(" | ", "? super ", ""))
            } else if (we.upperBounds.size() == 1 && reconstructTypeSignature(we.upperBounds.get(0)) == "Object") {
                return "?"
            } else {
                return we.upperBounds.stream().map(GroovyReconstructionSpec::reconstructTypeSignature).collect(Collectors.joining(" & ", "? extends ", ""))
            }
        } else {
            def boundTypeArguments = classElement.getBoundGenericTypes()
            if (boundTypeArguments.isEmpty()) {
                return classElement.getSimpleName()
            } else {
                return classElement.getSimpleName() +
                        boundTypeArguments.stream().map(GroovyReconstructionSpec::reconstructTypeSignature).collect(Collectors.joining(", ", "<", ">"))
            }
        }
    }

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

class Test<T> {
    $fieldType field;
}
class Wrapper {
    Test<String> test;
}
""")
        def field = element.getEnclosedElement(ElementQuery.ALL_FIELDS.named(s -> s == 'test')).get()
                .genericType.getEnclosedElement(ElementQuery.ALL_FIELDS.named(s -> s == 'field')).get()

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

class Test<T> {
    $fieldType field;
}
class Wrapper<U> {
    Test<U> test;
}
""")
        def field = element.getFields()[0].genericType.getEnclosedElement(ElementQuery.ALL_FIELDS.named(s -> s == 'field')).get()

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

class Test<T> {
    $fieldType field;
}
class Wrapper<U> {
    Test<U> test;
}
""")
        def field = element.getEnclosedElement(ElementQuery.ALL_FIELDS.named(s -> s == 'test')).get()
                .genericType.getEnclosedElement(ElementQuery.ALL_FIELDS.named(s -> s == 'field')).get()

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

class Sup<$decl> {
}
class Sub<U> extends Sup<$params> {
}
""")
        def interfaceElement = buildClassElement("""
package example;

import java.util.*;

interface Sup<$decl> {
}
class Sub<U> implements Sup<$params> {
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

class Sup<$decl> {
}
class Sub<U> extends Sup<$params> {
}
""").withBoundGenericTypes([ClassElement.of(String)])
        def interfaceElement = buildClassElement("""
package example;

import java.util.*;

interface Sup<$decl> {
}
class Sub<U> implements Sup<$params> {
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

class Sup<$decl> {
}
class Sub<U> extends Sup<$params> {
}
""").withBoundGenericTypes([ClassElement.of(String)])
        def interfaceElement = buildClassElement("""
package example;

import java.util.*;

interface Sup<$decl> {
}
class Sub<U> implements Sup<$params> {
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

        expect:
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
}

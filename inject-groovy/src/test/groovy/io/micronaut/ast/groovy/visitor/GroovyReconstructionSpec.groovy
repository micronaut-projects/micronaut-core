package io.micronaut.ast.groovy.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.Experimental
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.WildcardElement
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
            def genericPlaceholderElement = (GenericPlaceholderElement) classElement
            def name = genericPlaceholderElement.variableName
            if (typeVarsAsDeclarations) {
                def bounds = genericPlaceholderElement.bounds
                if (reconstructTypeSignature(bounds[0]) != 'Object') {
                    name += bounds.stream().map(GroovyReconstructionSpec::reconstructTypeSignature).collect(Collectors.joining(" & ", " extends ", ""))
                }
            } else if (genericPlaceholderElement.resolved) {
                return reconstructTypeSignature(genericPlaceholderElement.resolved.get())
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
            def typeArguments = classElement.getTypeArguments().values()
            if (typeArguments.isEmpty()) {
                return classElement.getSimpleName()
            } else if (typeArguments.stream().allMatch { it.isRawType() }) {
                return classElement.getSimpleName()
            } else {
                return classElement.getSimpleName() +
                        typeArguments.stream().map(GroovyReconstructionSpec::reconstructTypeSignature).collect(Collectors.joining(", ", "<", ">"))
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
                'byte[]',
                'byte[][]',
                'List<String>',
                'List<T>',
                'List<T[]>',
                'List<T[][]>',
                'List<? extends CharSequence>',
                'List<? super String>',
                'List<? extends T[]>',
                'List<? extends T[][]>',
                'List<? extends T[][][]>',
                'List<? extends List<? extends T[]>[]>',
                'List<? extends List<? extends T[]>[][]>',
                'List<? extends List<? extends T[][]>[][]>',
                'List<? extends List>',
                'List<? extends List<?>>',
            ]
    }

    def 'field type is wildcard extending byte[]'() {
        given:
        def element = buildClassElement("""
package example;

import java.util.*;

class Test<T> {
    List<? extends byte[]> field;
}
""")
        def field = element.getFields()[0]

        expect:
        // Wildcards with arrays not supported yet
        reconstructTypeSignature(field.genericType) == 'List<byte[]>'
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

    @Unroll("field type is #fieldType")
    def 'bound field type'() {
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
    def 'bound field type 2'() {
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
        'List<T>'                               | 'List<T>'
        'List<T[]>'                             | 'List<T[]>'
        'List<? extends CharSequence>'          | 'List<? extends CharSequence>'
        'List<? super String>'                  | 'List<? super String>'
        'List<? extends T[]>'                   | 'List<? extends T[]>'
        'List<? extends List<? extends T[]>[]>' | 'List<? extends List<? extends T[]>[]>'
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

            def get = element.getEnclosedElement(ElementQuery.ALL_FIELDS.named(s -> s == 'test')).get()
            def field = get
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

    def 'bound super type'() {
        given:
        def superElement = buildClassElement("""
package example;

import java.util.*;

class Sup<$decl> {
}
class Sub<U> extends Sup<$params> {
}
""").withTypeArguments([ClassElement.of(String)])
        def interfaceElement = buildClassElement("""
package example;

import java.util.*;

interface Sup<$decl> {
}
class Sub<U> implements Sup<$params> {
}
""").withTypeArguments([ClassElement.of(String)])

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

    def 'bound super type 2'() {
        given:
        def superElement = buildClassElement("""
package example;

import java.util.*;

class Sup<$decl> {
}
class Sub<U> extends Sup<$params> {
}
""").withTypeArguments([ClassElement.of(String)])
        def interfaceElement = buildClassElement("""
package example;

import java.util.*;

interface Sup<$decl> {
}
class Sub<U> implements Sup<$params> {
}
""").withTypeArguments([ClassElement.of(String)])

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

    def 'distinguish base list type'() {
        given:
            def classElement = buildClassElement("""
package example;

import java.util.*;
import java.lang.Number;

abstract class Base<T> {
    List field1;
    List<?> field2;
    List<Object> field3;
    List<T> field4;
}

class Test extends Base<String> {
}

""")
            def rawType = classElement.fields[0].type
            def wildcardType = classElement.fields[1].type
            def objectType = classElement.fields[2].type
            def genericType = classElement.fields[3].type

        expect:
            rawType.typeArguments["E"].type.name == "java.lang.Object"
            rawType.typeArguments["E"].isRawType()
            !rawType.typeArguments["E"].isWildcard()
            rawType.typeArguments["E"].isGenericPlaceholder()

            wildcardType.typeArguments["E"].type.name == "java.lang.Object"
            wildcardType.typeArguments["E"].isWildcard()
            !((WildcardElement)wildcardType.typeArguments["E"]).isBounded()
            !wildcardType.typeArguments["E"].isRawType()

            objectType.typeArguments["E"].type.name == "java.lang.Object"
            !objectType.typeArguments["E"].isWildcard()
            !objectType.typeArguments["E"].isRawType()
            !objectType.typeArguments["E"].isGenericPlaceholder()

            genericType.typeArguments["E"].type.name == "java.lang.Object"
            !genericType.typeArguments["E"].isWildcard()
            !genericType.typeArguments["E"].isRawType()
            genericType.typeArguments["E"].isGenericPlaceholder()
            (genericType.typeArguments["E"] as GenericPlaceholderElement).getResolved().isEmpty()
    }

    def 'distinguish base list generic type'() {
        given:
            def classElement = buildClassElement("""
package example;

import java.util.*;
import java.lang.Number;

abstract class Base<T> {
    List field1;
    List<?> field2;
    List<Object> field3;
    List<T> field4;
}

class Test extends Base<String> {
}

""")
            def rawType = classElement.fields[0].genericType
            def wildcardType = classElement.fields[1].genericType
            def objectType = classElement.fields[2].genericType
            def genericType = classElement.fields[3].genericType

        expect:
            rawType.typeArguments["E"].type.name == "java.lang.Object"
            rawType.typeArguments["E"].isRawType()
            !rawType.typeArguments["E"].isWildcard()
            rawType.typeArguments["E"].isGenericPlaceholder()

            wildcardType.typeArguments["E"].type.name == "java.lang.Object"
            wildcardType.typeArguments["E"].isWildcard()
            !((WildcardElement)wildcardType.typeArguments["E"]).isBounded()
            !wildcardType.typeArguments["E"].isRawType()

            objectType.typeArguments["E"].type.name == "java.lang.Object"
            !objectType.typeArguments["E"].isWildcard()
            !objectType.typeArguments["E"].isRawType()
            !objectType.typeArguments["E"].isGenericPlaceholder()

            genericType.typeArguments["E"].type.name == "java.lang.String"
            !genericType.typeArguments["E"].isWildcard()
            !genericType.typeArguments["E"].isRawType()
            genericType.typeArguments["E"].isGenericPlaceholder()
            def resolved = (genericType.typeArguments["E"] as GenericPlaceholderElement).getResolved().get()
            resolved.name == "java.lang.String"
            !resolved.isWildcard()
            !resolved.isRawType()
            !resolved.isGenericPlaceholder()
    }
}

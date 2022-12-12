package io.micronaut.kotlin.processing.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.GenericPlaceholderElement
import spock.lang.PendingFeature
import spock.lang.Unroll

class KotlinReconstructionSpec extends AbstractKotlinCompilerSpec {
    @Unroll("field type is #fieldType")
    def 'field type'() {
        given:
        def element = buildClassElement("example.Test", """
package example;

import java.util.*;

class Test<T> {
    lateinit var field : $fieldType
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
                'List<Array<T>>',
                'List<out CharSequence>',
//                'List<in CharSequence>', // doesn't work?
                'List<out Array<T>>',
                'List<out Array<List<out Array<T>>>>'
        ]
    }

    @Unroll("super type is #superType")
    def 'super type'() {
        given:
        def element = buildClassElement("example.Test", """
package example;

import java.util.*;

abstract class Test<T> : $superType() {
}
""")

        expect:
        reconstructTypeSignature(element.superType.get()) == superType

        where:
        superType << [
//                'AbstractList', raw types not supported
                'AbstractList<String>',
                'AbstractList<T>',
                'AbstractList<Array<T>>',
                'AbstractList<List<out CharSequence>>',
                'AbstractList<List<out Array<T>>>',
                'AbstractList<Array<List<out Array<T>>>>',
                'AbstractList<List<*>>'
        ]
    }

    @Unroll("super interface is #superType")
    def 'super interface'() {
        given:
        def element = buildClassElement("example.Test", """
package example;

import java.util.*;

abstract class Test<T> : $superType {
}
""")

        expect:
        reconstructTypeSignature(element.interfaces[0]) == superType

        where:
        superType << [
//                'List',
                'List<String>',
                'List<T>',
                'List<Array<T>>',
                'List<List<out CharSequence>>',
//                'List<List<in String>>',
                'List<List<out Array<T>>>',
                'List<Array<List<out Array<T>>>>',
//                'List<List>',
                'List<List<Any>>',
        ]
    }

    @Unroll("type var is #decl")
    @PendingFeature
    def 'type vars declared on type'() {
        given:
        def element = buildClassElement("example.Test", """
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
                'out T : CharSequence',
                'T : A',
//                'T extends List',
//                'T extends List<?>',
//                'T extends List<T>',
//                'T extends List<? extends T>',
//                'T extends List<? extends A>',
//                'T extends List<T[]>',
        ]
    }

    @Unroll('declaration is #decl')
    def 'fold type variable to null'() {
        given:
        def classElement = buildClassElement("example.Test", """
package example;

import java.util.*;

class Test<T> {
    lateinit var field : $decl;
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
        'List<out T>'       | 'List'
//        'List<? super T>'   | 'List'
    }
}

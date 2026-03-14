package io.micronaut.kotlin.processing.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.WildcardElement
import spock.lang.Unroll

class KotlinReconstructionSpec extends AbstractKotlinCompilerSpec {

    @Unroll("field type is #fieldType")
    def 'field type'() {
        when:
            def value = buildClassElementMapped("test.Test", """
package test;

import java.util.*;

class Test<T> {
    lateinit var field : $fieldType
}

class Lst<in E> {
}
""") {reconstructTypeSignature(it.getFields()[0].genericType) }

        then:
            value == fieldType

        where:
            fieldType << [
                    'String',
                    'List<String>',
                    'List<Any>',
                    'List<T>',
                    'List<Array<T>>',
                    'List<out CharSequence>',
                    'List<out Array<T>>',
                    'List<out Array<T>>',
                    'List<out Array<List<out Array<T>>>>',
                    'Lst<String>',
                    'Lst<in CharSequence>',
                    'Lst<Lst<in String>>'
            ]
    }

    @Unroll("type var is #decl")
    def 'type vars declared on method'() {
        given:
            def methodDecl = buildClassElementMapped("test.Test", """
package test;

import java.util.*;

abstract class Test<A> {
    fun <$decl> method() {}
}
class Lst<in E> {
}
""", {
                element ->
                    def method = element.<MethodElement> getEnclosedElement(ElementQuery.ALL_METHODS.filter(el -> el.name == 'method')).get()
                    return reconstructTypeSignature(method.declaredTypeVariables[0], true)
            })
        expect:
            methodDecl == decl
        where:
            decl << [
//                    'T',
'T : CharSequence',
'T : A',
'T : List<*>',
'T : List<T>',
'T : List<out T>',
'T : List<out A>',
'T : List<Array<T>>',
'T : Lst<in T>',
'T : Lst<in A>',
'T : Lst<Array<T>>'
            ]
    }

    @Unroll("super type is #superType")
    def 'super type'() {
        when:
            def value = buildClassElementMapped("test.Test", """
package test;

import java.util.*;

abstract class Test<T> : $superType() {
}
""") { reconstructTypeSignature(it.superType.get())}

        then:
            value == superType

        where:
            superType << [
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
        when:
            def value = buildClassElementMapped("test.Test", """
package test;

import java.util.*;

abstract class Test<T> : $superType {
}
""") {reconstructTypeSignature(it.interfaces[0])}

        then:
            value == superType

        where:
            superType << [
                    'List<String>',
                    'List<T>',
                    'List<Array<T>>',
                    'List<List<out CharSequence>>',
                    'List<List<out Array<T>>>',
                    'List<Array<List<out Array<T>>>>',
                    'List<List<Any>>',
            ]
    }

    @Unroll("type var is #decl")
    def 'type vars declared on type'() {
        given:
            def elementDecl = buildClassElementMapped("test.Test", """
package test;

import java.util.*;

abstract class Test<A, $decl> {
}

class Lst<in E> {
}
""", element -> {
                reconstructTypeSignature(element.declaredGenericPlaceholders[1], true)
            })

        expect:
            elementDecl == decl

        where:
            decl << [
//                  'T',
'T : A',
'T : List<*>',
'T : List<T>',
'T : List<out T>',
'T : List<out A>',
'T : List<Array<T>>',
'T : Lst<in A>',
'T : Lst<in T>',
'T : Lst<in Array<T>>'
            ]
    }

    @Unroll('declaration is #decl')
    def 'fold type variable to null'() {
        given:
            def classDecl = buildClassElementMapped("test.Test", """
package test;

import java.util.*;

class Test<T> {
    lateinit var field : $decl;
}

class Lst<in E> {
}
""", {
                def fieldType = it.fields[0].type
                return reconstructTypeSignature(fieldType.foldBoundGenericTypes {
                    if (it != null && it.isGenericPlaceholder() && ((GenericPlaceholderElement) it).variableName == 'T') {
                        return null
                    } else {
                        return it
                    }
                })
            })

        expect:
            classDecl == expected

        where:
            decl             | expected
            'String'         | 'String'
            'List<T>'        | 'List'
            'Map<Object, T>' | 'Map'
            'List<out T>'    | 'List'
            'Lst<in T>'      | 'Lst'
    }

    @Unroll("field type is #fieldType")
    def 'bound field type'() {
        when:
            def value = buildClassElementMapped("test.Wrapper", """
package test;

import java.util.*;

class Wrapper {
    var test: Test<String>? = null;
}
class Test<T> {
    var field: $fieldType? = null;
}
class Lst<in E> {
}
""") { reconstructTypeSignature(it.getFields()[0].genericType.getFields()[0].genericType)}

        then:
            value == expectedType

        where:
            fieldType                             | expectedType
            'String'                              | 'String'
            'List<String>'                        | 'List<String>'
            'List<*>'                             | 'List<*>'
            'List<T>'                             | 'List<String>'
            'List<Array<T>>'                      | 'List<Array<String>>'
            'List<out CharSequence>'              | 'List<out CharSequence>'
            'Lst<in String>'                      | 'Lst<in String>'
            'List<out Array<T>>'                  | 'List<out Array<String>>'
            'List<out Array<List<out Array<T>>>>' | 'List<out Array<List<out Array<String>>>>'
//            'List<out List<*>>'                   | 'List<out List<*>>'
    }


    @Unroll("field type is #fieldType")
    def 'bound field type to other variable'() {
        when:
            def value = buildClassElementMapped("test.Wrapper", """
package test;

import java.util.*;

class Wrapper<U> {
    var test: Test<U>? = null;
}
class Test<T> {
    var field: $fieldType? = null;
}
class Lst<in E> {
}
""") {reconstructTypeSignature(it.getFields()[0].genericType.getFields()[0].genericType) }

        then:
            value == expectedType

        where:
            fieldType                             | expectedType
            'String'                              | 'String'
            'List<String>'                        | 'List<String>'
            'List<*>'                             | 'List<*>'
            'List<T>'                             | 'List<U>'
            'List<Array<T>>'                      | 'List<Array<U>>'
            'List<out CharSequence>'              | 'List<out CharSequence>'
            'Lst<in String>'                      | 'Lst<in String>'
            'List<out Array<T>>'                  | 'List<out Array<U>>'
            'List<out Array<List<out Array<T>>>>' | 'List<out Array<List<out Array<U>>>>'
//            'List<out List<*>>'                   | 'List<out List<*>>'
    }

    def 'unbound super type'() {
        when:
            def superElementValue = buildClassElementMapped("test.Sub", """
package test;

import java.util.*;

class Sub<U> : Sup<$params>() {
}
open class Sup<$decl> {
}
class Lst<in E> {
}
""") {reconstructTypeSignature(it.getSuperType().get()) }
            def interfaceElementValue = buildClassElementMapped("test.Sub", """
package test;

import java.util.*;

class Sub<U> : Sup<$params> {
}
interface Sup<$decl> {
}
class Lst<in E> {
}
""") { reconstructTypeSignature(it.getInterfaces()[0]) }

        then:
            superElementValue == expected
            interfaceElementValue == expected

        where:
            decl | params        | expected
            'T'  | 'String'      | 'Sup<String>'
            'T'  | 'List<U>'     | 'Sup<List<U>>'
            'T'  | 'List<out U>' | 'Sup<List<out U>>'
            'T'  | 'Lst<in U>'   | 'Sup<Lst<in U>>'
    }

    def 'bound super type'() {
        when:
            def superElementValue = buildClassElementMapped("test.Sub", """
package test;

class Sub<U> : Sup<$params>() {
}
open class Sup<$decl> {
}
class MyList<out E>
class Lst<in E>
class Str
""", { ce ->
                ce = ce.withTypeArguments([ClassElement.of("test.Str")])
                initializeAllTypeArguments(ce)
                return reconstructTypeSignature(ce.getSuperType().get())
            })
            def interfaceElementValue = buildClassElementMapped("test.Sub", """
package test;

class Sub<U> : Sup<$params> {
}
interface Sup<$decl> {
}
class MyList<out E>
class Lst<in E>
class Str
""", { ce ->
                ce = ce.withTypeArguments([ClassElement.of("test.Str")])
                initializeAllTypeArguments(ce)
                return reconstructTypeSignature(ce.getInterfaces()[0])
            })

        then:
            superElementValue == expected
            interfaceElementValue == expected

        where:
            decl | params          | expected
            'T'  | 'Str'           | 'Sup<Str>'
            'T'  | 'MyList<U>'     | 'Sup<MyList<Str>>'
            'T'  | 'MyList<out U>' | 'Sup<MyList<out Str>>'
            'T'  | 'Lst<in U>'     | 'Sup<Lst<in Str>>'
    }

    @Unroll('declaration is #decl')
    def 'fold type variable'() {
        given:
            def fieldType = buildClassElementTransformed("test.Test", """
package test;

class Test<T> {
    var field : $decl? = null;
}
class MyMap<out K, out V>
class MyList<out E>
class Lst<in E>
class Str
""", {
                def fieldType = it.fields[0].type.foldBoundGenericTypes {
                    if (it.isGenericPlaceholder() && ((GenericPlaceholderElement) it).variableName == 'T') {
                        return ClassElement.of("test.Str")
                    } else {
                        return it
                    }
                }

                initializeAllTypeArguments(fieldType)
                return fieldType
            })

        expect:
            reconstructTypeSignature(fieldType) == expected

        where:
            decl            | expected
            'Str'           | 'Str'
            'T'             | 'Str'
            'MyList<T>'     | 'MyList<Str>'
            'MyMap<Any, T>' | 'MyMap<Any, Str>'
            'MyList<out T>' | 'MyList<out Str>'
            'Lst<in T>'     | 'Lst<in Str>'
    }

    def 'distinguish list types'() {
        given:
            def classElement = buildClassElement("test.Test", """
package test;

import java.util.*;

class Test {
    var field1: List<*>? = null
    var field2: List<*>? = null
    var field3: List<Any>? = null
}
""")
            def rawType = classElement.fields[0].genericType
            def wildcardType = classElement.fields[1].genericType
            def objectType = classElement.fields[2].genericType

        expect:
//            rawType.boundGenericTypes.isEmpty()
            rawType.typeArguments["E"].type.name == "java.lang.Object"
            rawType.typeArguments["E"].isRawType()
            rawType.typeArguments["E"].isWildcard()
            !rawType.typeArguments["E"].isGenericPlaceholder()

//            wildcardType.boundGenericTypes.size() == 1
//            wildcardType.boundGenericTypes[0].isWildcard()
//            wildcardType.typeArguments["E"].type.name == "java.lang.Object"
//            wildcardType.typeArguments["E"].isWildcard()
//            !wildcardType.typeArguments["E"].isRawType()

            objectType.boundGenericTypes.size() == 1
            !objectType.boundGenericTypes[0].isWildcard()
            objectType.typeArguments["E"].type.name == "java.lang.Object"
            !objectType.typeArguments["E"].isWildcard()
            !objectType.typeArguments["E"].isRawType()
            !objectType.typeArguments["E"].isGenericPlaceholder()
    }

    def 'distinguish list types 2'() {
        expect:
            buildClassElement("test.Test", """
package test;

import java.util.*;
import java.lang.Number;

class Test {
    var field1: List<*>? = null
    var field2: List<*>? = null
    var field3: List<Any>? = null
    var field4: List<String>? = null
    var field5: List<out Number>? = null
}
""") { classElement ->
                def rawType = classElement.fields[0].type
                def wildcardType = classElement.fields[1].type
                def objectType = classElement.fields[2].type
                def stringType = classElement.fields[3].type
                def numberType = classElement.fields[4].type

                assert rawType.typeArguments["E"].type.name == "java.lang.Object"
                assert rawType.typeArguments["E"].isRawType()
                assert rawType.typeArguments["E"].isWildcard()
                assert !rawType.typeArguments["E"].isGenericPlaceholder()

//            wildcardType.typeArguments["E"].type.name == "java.lang.Object"
//            wildcardType.typeArguments["E"].isWildcard()
//            !((WildcardElement)wildcardType.typeArguments["E"]).isBounded()
//            !wildcardType.typeArguments["E"].isRawType()

                assert objectType.typeArguments["E"].type.name == "java.lang.Object"
                assert !objectType.typeArguments["E"].isWildcard()
                assert !objectType.typeArguments["E"].isRawType()
                assert !objectType.typeArguments["E"].isGenericPlaceholder()

                assert stringType.typeArguments["E"].type.name == "java.lang.String"
                assert !stringType.typeArguments["E"].isWildcard()
                assert !stringType.typeArguments["E"].isRawType()
                assert !stringType.typeArguments["E"].isGenericPlaceholder()

                assert numberType.typeArguments["E"].type.name == "java.lang.Number"
                assert numberType.typeArguments["E"].isWildcard()
                assert ((WildcardElement) numberType.typeArguments["E"]).isBounded()
                assert !numberType.typeArguments["E"].isRawType()
            }
    }

    def 'distinguish base list type'() {
        given:
            def classElement = buildClassElement("test.Test", """
package test;

import java.util.*;
import java.lang.Number;

class Test : Base<String>() {
}

abstract class Base<T> {
    var field1: List<*>? = null
    var field2: List<*>? = null
    var field3: List<Any>? = null
    var field4: List<T>? = null
}

""")
            def rawType = classElement.fields[0].type
            def wildcardType = classElement.fields[1].type
            def objectType = classElement.fields[2].type
            def genericType = classElement.fields[3].type

        expect:
            rawType.typeArguments["E"].type.name == "java.lang.Object"
            rawType.typeArguments["E"].isRawType()
            rawType.typeArguments["E"].isWildcard()
            !rawType.typeArguments["E"].isGenericPlaceholder()

//            wildcardType.typeArguments["E"].type.name == "java.lang.Object"
//            wildcardType.typeArguments["E"].isWildcard()
//            !((WildcardElement)wildcardType.typeArguments["E"]).isBounded()
//            !wildcardType.typeArguments["E"].isRawType()

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
        expect:
            buildClassElement("test.Test", """
package test;

import java.util.*;
import java.lang.Number;

class Test : Base<String>() {
}

abstract class Base<T> {
    var field1: List<*>? = null
    var field2: List<*>? = null
    var field3: List<Any>? = null
    var field4: List<T>? = null
}

""") { classElement ->
                def rawType = classElement.fields[0].genericType
                def wildcardType = classElement.fields[1].genericType
                def objectType = classElement.fields[2].genericType
                def genericType = classElement.fields[3].genericType

                assert rawType.typeArguments["E"].type.name == "java.lang.Object"
                assert rawType.typeArguments["E"].isRawType()
                assert rawType.typeArguments["E"].isWildcard()
                assert !rawType.typeArguments["E"].isGenericPlaceholder()

//            wildcardType.typeArguments["E"].type.name == "java.lang.Object"
//            wildcardType.typeArguments["E"].isWildcard()
//            !((WildcardElement)wildcardType.typeArguments["E"]).isBounded()
//            !wildcardType.typeArguments["E"].isRawType()

                assert objectType.typeArguments["E"].type.name == "java.lang.Object"
                assert !objectType.typeArguments["E"].isWildcard()
                assert !objectType.typeArguments["E"].isRawType()
                assert !objectType.typeArguments["E"].isGenericPlaceholder()

                assert genericType.typeArguments["E"].type.name == "java.lang.String"
                assert !genericType.typeArguments["E"].isWildcard()
                assert !genericType.typeArguments["E"].isRawType()
                assert genericType.typeArguments["E"].isGenericPlaceholder()

                def resolved = (genericType.typeArguments["E"] as GenericPlaceholderElement).getResolved().get()
                assert resolved.name == "java.lang.String"
                assert !resolved.isWildcard()
                assert !resolved.isRawType()
                assert resolved.isGenericPlaceholder()
                assert (resolved as GenericPlaceholderElement).declaringElement.get().name == "test.Base"
            }
    }

    private void initializeAllTypeArguments(ClassElement type) {
        initializeAllTypeArguments0(type, 0)
    }

    private void initializeAllTypeArguments0(ClassElement type, int level) {
        if (level == 4) {
            return
        }
        type.getName()
        type.getAnnotationNames()
        type.getAllTypeArguments().entrySet().forEach { e1 ->
            e1.value.entrySet().forEach { e2 ->
                initializeAllTypeArguments0(e2.value, level + 1)
            }
        }
        if (type.isWildcard()) {
            def we = type as WildcardElement
            if (we.isRawType()) {
                return
            }
            if (!we.lowerBounds.isEmpty()) {
                we.lowerBounds.forEach {
                    initializeAllTypeArguments0(it, level + 1)
                }
            } else {
                we.upperBounds.forEach {
                    initializeAllTypeArguments0(it, level + 1)
                }
            }
        }
    }
}

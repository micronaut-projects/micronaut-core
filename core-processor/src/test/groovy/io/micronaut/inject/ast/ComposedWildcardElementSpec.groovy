package io.micronaut.inject.ast

import spock.lang.Specification

class ComposedWildcardElementSpec extends Specification {

    void "test an upper bounded wildcard acts as its upper bound"() {
        given:
        def upper = ClassElement.of(CharSequence)
        def wildcard = WildcardElement.of([upper], [])

        expect:
        wildcard.upperBounds == [upper]
        wildcard.lowerBounds.isEmpty()
        wildcard.isBounded()
        wildcard.hasExplicitUpperBound()
        !wildcard.hasExplicitLowerBound()
        wildcard.name == CharSequence.name
        wildcard.isAssignable(CharSequence)
        !wildcard.isAssignable(String)
        wildcard.isInterface()
        wildcard.toString() == '? extends java.lang.CharSequence'
        wildcard == WildcardElement.of([upper], [])
    }

    void "test a lower bounded wildcard"() {
        given:
        def wildcard = WildcardElement.of([ClassElement.of(Object)], [ClassElement.of(String)])

        expect:
        wildcard.lowerBounds*.name == [String.name]
        wildcard.hasExplicitLowerBound()
        !wildcard.hasExplicitUpperBound()
        wildcard.name == Object.name
        wildcard.toString() == '? super java.lang.String'
    }

    void "test an unbounded wildcard"() {
        given:
        def wildcard = WildcardElement.of([ClassElement.of(Object)], [])

        expect:
        !wildcard.isBounded()
        !wildcard.hasExplicitUpperBound()
        !wildcard.hasExplicitLowerBound()
        wildcard.toString() == '?'
    }

    void "test a wildcard has an upper bound"() {
        when:
        WildcardElement.of([], [])

        then:
        thrown(IllegalArgumentException)
    }

    void "test a wildcard delegates to its first upper bound"() {
        given:
        def superType = ClassElement.of(Object)
        def typeArgument = ClassElement.of(String)
        def upper = Mock(ClassElement) {
            getSuperType() >> Optional.of(superType)
            getInterfaces() >> [typeArgument]
            getTypeArguments() >> [T: typeArgument]
            getAllTypeArguments() >> ['test.Upper': [T: typeArgument]]
            getBoundGenericTypes() >> [typeArgument]
            isProtected() >> true
            isPublic() >> false
            getNativeType() >> 'native'
            isAssignable(typeArgument) >> true
        }
        def wildcard = WildcardElement.of([upper], [])

        expect:
        wildcard.superType.get() == superType
        wildcard.interfaces == [typeArgument]
        wildcard.typeArguments == [T: typeArgument]
        wildcard.allTypeArguments == ['test.Upper': [T: typeArgument]]
        wildcard.boundGenericTypes == [typeArgument]
        wildcard.isProtected()
        !wildcard.isPublic()
        wildcard.nativeType == 'native'
        wildcard.genericNativeType.is(wildcard)
        wildcard.isAssignable(typeArgument)
    }

    void "test a wildcard is neither an array nor an array component"() {
        given:
        def wildcard = WildcardElement.of([ClassElement.of(Object)], [])

        when:
        wildcard.toArray()

        then:
        thrown(UnsupportedOperationException)

        when:
        wildcard.fromArray()

        then:
        thrown(UnsupportedOperationException)
    }
}

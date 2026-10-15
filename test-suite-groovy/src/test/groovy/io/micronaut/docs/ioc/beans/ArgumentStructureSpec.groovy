package io.micronaut.docs.ioc.beans

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.core.type.WildcardArgument
import spock.lang.Specification

class ArgumentStructureSpec extends Specification {

    void "test argument structure"() {
        given:
        // tag::structure[]
        def introspection = BeanIntrospection.getIntrospection(Catalog)
        Argument<?> prices = introspection.getRequiredProperty("prices", List).asArgument() // List<? extends Number>
        Argument<?> items = introspection.getRequiredProperty("items", List).asArgument() // List<T>
        Argument<?> samples = introspection.getRequiredProperty("samples", Object[]).asArgument() // T[]

        Argument<?> wildcard = prices.typeParameters[0]
        assert wildcard.isWildcard() // <1>
        assert ((WildcardArgument<?>) wildcard).upperBounds[0].type == Number

        Argument<?> variable = items.typeParameters[0]
        assert variable.isUnresolvedTypeVariable() // <2>
        assert ((GenericPlaceholder<?>) variable).variableName == "T"

        assert samples.componentType().equalsStructure(variable) // <3>
        assert variable.arrayType().equalsStructure(samples)

        Argument<?> listOfNumber = Argument.listOf(Number)
        assert prices.equalsType(listOfNumber) // <4>
        assert !prices.equalsStructure(listOfNumber)
        // end::structure[]

        expect:
        wildcard.isWildcard()
    }
}

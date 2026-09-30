package io.micronaut.docs.ioc.beans

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.core.type.WildcardArgument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArgumentStructureSpec {

    @Test
    fun testArgumentStructure() {
        // tag::structure[]
        val introspection = BeanIntrospection.getIntrospection(Catalog::class.java)
        val prices = introspection.getRequiredProperty("prices", MutableList::class.java).asArgument() // MutableList<out Number>
        val items = introspection.getRequiredProperty("items", MutableList::class.java).asArgument() // MutableList<T>
        val samples = introspection.getRequiredProperty("samples", Array<Any>::class.java).asArgument() // Array<T>

        val wildcard = prices.typeParameters[0]
        assertTrue(wildcard.isWildcard) // <1>
        assertEquals(Number::class.java, (wildcard as WildcardArgument<*>).upperBounds[0].type)

        val variable = items.typeParameters[0]
        assertTrue(variable.isUnresolvedTypeVariable) // <2>
        assertEquals("T", (variable as GenericPlaceholder<*>).variableName)

        assertTrue(samples.componentType()!!.equalsStructure(variable)) // <3>
        assertTrue(variable.arrayType().equalsStructure(samples))

        val listOfNumber = Argument.listOf(Number::class.java)
        assertTrue(prices.equalsType(listOfNumber)) // <4>
        assertFalse(prices.equalsStructure(listOfNumber))
        // end::structure[]
    }
}

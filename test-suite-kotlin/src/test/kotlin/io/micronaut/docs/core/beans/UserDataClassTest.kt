package io.micronaut.docs.core.beans

import io.micronaut.core.beans.BeanIntrospection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserDataClassTest {

    @Test
    fun aDataClassCanBeInstantiatedViaBeanIntrospection() {
//tag::dataclassbeanintrospectioninstantiate[]
        val introspection: BeanIntrospection<UserDataClass> = BeanIntrospection.getIntrospection(UserDataClass::class.java)
        val user: UserDataClass = introspection.instantiate("John")
//end::dataclassbeanintrospectioninstantiate[]
        assertEquals("John", user.name)
    }
}
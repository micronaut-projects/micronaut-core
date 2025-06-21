package io.micronaut.docs.core.beans

import io.micronaut.core.beans.BeanIntrospection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class UserDataClassTest {

    @Test
    fun aDataClassCanBeInstantiatedViaBeanIntrospection() {
        val introspection: BeanIntrospection<UserDataClass> = assertDoesNotThrow { BeanIntrospection.getIntrospection(UserDataClass::class.java) }
        val user: UserDataClass = introspection.instantiate("John")
        assertEquals("John", user.name)
    }
}
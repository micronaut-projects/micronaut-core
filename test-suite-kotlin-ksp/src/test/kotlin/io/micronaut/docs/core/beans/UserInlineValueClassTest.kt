package io.micronaut.docs.core.beans

import io.micronaut.core.beans.BeanIntrospection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class UserInlineValueClassTest {

    @Disabled("Kotlin inline value classes not yet supported via Bean Introspection")
    @Test
    fun anInlineValueClassNotYetSupportedViaBeanIntrospection() {
        val introspection: BeanIntrospection<UserInlineValueClass> = assertDoesNotThrow { BeanIntrospection.getIntrospection(UserInlineValueClass::class.java) }
        val user: UserInlineValueClass = introspection.instantiate("John")
        assertEquals("John", user.name)
    }
}

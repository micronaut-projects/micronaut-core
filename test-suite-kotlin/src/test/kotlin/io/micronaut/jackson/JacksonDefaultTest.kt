package io.micronaut.jackson

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

@MicronautTest
class JacksonDefaultTest {

    @Test
    fun testValueMissingOnDefaultField(objectMapper: ObjectMapper) {
        val json = """{}"""
        val bean = objectMapper.readValue(json, DefaultConstructorDto::class.java)
        Assertions.assertEquals(22, bean.longField)
    }

    @Test
    fun defaultPrimitiveIsUsedIfMissingOnRequiredField(objectMapper: ObjectMapper) {
        val json = """{}"""
        val bean = objectMapper.readValue(json, RequireConstructorParamDto::class.java)
        Assertions.assertEquals(0, bean.longField)
    }

    @Test
    fun noExceptionIsThrownWhenFailOnNullForPrimitiveIsEnabledAndPrimitiveFieldHasDefault(objectMapper: ObjectMapper) {
        val configuredObjectMapper =
            objectMapper.copy().configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
        val json = """{}"""

        val bean: DefaultConstructorDto = assertDoesNotThrow {
            configuredObjectMapper.readValue(json, DefaultConstructorDto::class.java)
        }
        Assertions.assertEquals(22, bean.longField)
    }

    @Test
    fun throwExceptionWhenFailOnNullForPrimitiveIsEnabledAndPrimitiveFieldIsMissing(objectMapper: ObjectMapper) {
        val configuredObjectMapper =
            objectMapper.copy().configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
        val json = """{}"""
        val exception = Assertions.assertThrows(
            MismatchedInputException::class.java,
            { configuredObjectMapper.readValue(json, RequireConstructorParamDto::class.java) })
        Assertions.assertTrue(exception.message!!.contains("Cannot map `null` into type `long`"))
    }
}

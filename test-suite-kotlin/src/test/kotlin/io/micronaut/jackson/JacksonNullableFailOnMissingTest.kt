package io.micronaut.jackson

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.exc.MismatchedInputException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest
class JacksonNullableFailOnMissingTest {

    @Test
    fun testDefaultValue(objectMapper: ObjectMapper) {
        val result = objectMapper.writeValueAsString(NullConstructorDto())
        val bean = objectMapper.readValue(result, NullConstructorDto::class.java)
        Assertions.assertEquals(null, bean.longField)
    }

    @Test
    fun testNonNullValue(objectMapper: ObjectMapper) {
        val e = Assertions.assertThrows(MismatchedInputException::class.java) {
            objectMapper.readValue("{}", NonNullConstructorDto::class.java)
        }
        Assertions.assertTrue(e.message!!.contains("Cannot map `null` into type `long`"))
    }

    @Test
    fun testNonNullValue2(objectMapper: ObjectMapper) {
        val e = Assertions.assertThrows(MismatchedInputException::class.java) {
            objectMapper.readValue("{\"longField\":null}", NonNullConstructorDto::class.java)
        }
        Assertions.assertTrue(e.message!!.contains("Cannot map `null` into type `long`"))
    }

    @Test
    fun testNullPropertyValue(objectMapper: ObjectMapper) {
        val bean = objectMapper.readValue("{}", NullPropertyDto::class.java)
        Assertions.assertEquals(null, bean.longField)
    }
}

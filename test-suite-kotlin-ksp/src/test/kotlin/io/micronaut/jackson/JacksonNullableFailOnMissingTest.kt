package io.micronaut.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Property(name = "jackson.deserialization.failOnNullForPrimitives", value = "true")
@MicronautTest
class JacksonNullableFailOnMissingTest {

    @Test
    fun testDefaultValue(objectMapper: ObjectMapper) {
        val result = objectMapper.writeValueAsString(NullDto())
        val bean = objectMapper.readValue(result, NullDto::class.java)
        Assertions.assertEquals(null, bean.longField)
    }

    @Test
    fun testNonNullValue(objectMapper: ObjectMapper) {
        val e = Assertions.assertThrows(MismatchedInputException::class.java) {
            objectMapper.readValue("{}", NonNullDto::class.java)
        }
        Assertions.assertTrue(e.message!!.contains("Cannot map `null` into type `long`"))
    }

}

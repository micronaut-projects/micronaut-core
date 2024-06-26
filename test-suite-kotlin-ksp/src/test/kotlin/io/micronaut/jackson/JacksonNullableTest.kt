package io.micronaut.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest
class JacksonNullableTest {

    @Test
    fun testDefaultValue(objectMapper: ObjectMapper) {
        val result = objectMapper.writeValueAsString(NullDto())
        val bean = objectMapper.readValue(result, NullDto::class.java)
        Assertions.assertEquals(null, bean.longField)
    }

    @Test
    fun testNonNullValue(objectMapper: ObjectMapper) {
        val bean = objectMapper.readValue("{}", NonNullDto::class.java)
        Assertions.assertEquals(0, bean.longField)
    }

}

package io.micronaut.jackson

import io.micronaut.context.annotation.Property
import tools.jackson.databind.ObjectMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Property(name = "jackson.deserialization.failOnNullForPrimitives", value = "false")
@MicronautTest
class JacksonNullableTest {

    @Test
    fun testDefaultValue(objectMapper: ObjectMapper) {
        val result = objectMapper.writeValueAsString(NullConstructorDto())
        val bean = objectMapper.readValue(result, NullConstructorDto::class.java)
        Assertions.assertEquals(null, bean.longField)
    }

    @Test
    fun testNonNullValue(objectMapper: ObjectMapper) {
        val bean = objectMapper.readValue("{}", NonNullConstructorDto::class.java)
        Assertions.assertEquals(0, bean.longField)
    }

    @Test
    fun testNonNullValue2(objectMapper: ObjectMapper) {
        val bean = objectMapper.readValue("{\"longField\":null}", NonNullConstructorDto::class.java)
        Assertions.assertEquals(0, bean.longField)
    }

    @Test
    fun testNullPropertyValue(objectMapper: ObjectMapper) {
        val bean = objectMapper.readValue("{}", NullPropertyDto::class.java)
        Assertions.assertEquals(null, bean.longField)
    }

}

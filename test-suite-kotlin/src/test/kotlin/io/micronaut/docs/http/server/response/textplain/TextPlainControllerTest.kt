package io.micronaut.docs.http.server.response.textplain

import io.micronaut.context.annotation.Property
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*

@Property(name = "spec.name", value = "TextPlainControllerTest")
@MicronautTest
class TextPlainControllerTest {

    @Inject
    @field:Client("/txt")
    lateinit var httpClient : HttpClient

    @Test
    fun textPlainBoolean() {
        val result = httpClient.toBlocking().retrieve("/boolean")
        Assertions.assertEquals("true", result)
    }

    @Test
    fun textPlainMonoBoolean() {
        val result = httpClient.toBlocking().retrieve("/boolean/mono")
        Assertions.assertEquals("true", result)
    }

    @Test
    fun textPlainFluxBoolean() {
        val result = httpClient.toBlocking().retrieve("/boolean/flux")
        Assertions.assertEquals("true", result)
    }

    @Test
    fun textPlainBigDecimal() {
        val result = httpClient.toBlocking().retrieve("/bigdecimal")
        Assertions.assertEquals(BigDecimal.valueOf(Long.MAX_VALUE).toString(), result)
    }

    @Test
    fun textPlainDate() {
        val result = httpClient.toBlocking().retrieve("/date")
        Assertions.assertEquals(Calendar.Builder().setDate(2023, 7, 4).build().toString(), result)
    }

    @Test
    fun textPlainPerson() {
        val result = httpClient.toBlocking().retrieve("/person")
        Assertions.assertEquals(Person("Dean Wette", 65).toString(), result)
    }
}

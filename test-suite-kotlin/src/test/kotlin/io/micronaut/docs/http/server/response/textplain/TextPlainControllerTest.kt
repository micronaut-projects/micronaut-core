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
internal class TextPlainControllerTest {

    @Inject
    @field:Client("/txt")
    lateinit var httpClient : HttpClient

    @Test
    fun textPlainBoolean() {
        asserTextResult("/boolean", "true")
    }

    @Test
    fun textPlainMonoBoolean() {
        asserTextResult("/boolean/mono", "true")
    }

    @Test
    fun textPlainFluxBoolean() {
        asserTextResult("/boolean/flux", "true")
    }

    @Test
    fun textPlainBigDecimal() {
        asserTextResult("/bigdecimal", BigDecimal.valueOf(Long.MAX_VALUE).toString())
    }

    @Test
    fun textPlainDate() {
        asserTextResult("/date", Calendar.Builder().setDate(2023, 7, 4).build().toString())
    }

    @Test
    fun textPlainPerson() {
        asserTextResult("/person", Person("Dean Wette", 65).toString())
    }

    private fun asserTextResult(url: String, expectedResult: String) {
        val result = httpClient.toBlocking().retrieve(url)
        Assertions.assertEquals(expectedResult, result)
    }
}

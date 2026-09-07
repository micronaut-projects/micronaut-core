package io.micronaut.docs.http.server.response.textplain

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Calendar

class TextPlainControllerTest {

    companion object {
        private lateinit var server: EmbeddedServer
        private lateinit var httpClient: HttpClient

        @BeforeAll
        @JvmStatic
        fun setup() {
            server = ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "TextPlainControllerTest"))
            httpClient = server.applicationContext.createBean(HttpClient::class.java, server.url)
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            httpClient.close()
            server.close()
        }
    }

    @Test
    fun textPlainBoolean() = assertTextResult("/txt/boolean", "true")

    @Test
    fun textPlainMonoBoolean() = assertTextResult("/txt/boolean/mono", "true")

    @Test
    fun textPlainFluxBoolean() = assertTextResult("/txt/boolean/flux", "true")

    @Test
    fun textPlainBigDecimal() = assertTextResult("/txt/bigdecimal", BigDecimal.valueOf(Long.MAX_VALUE).toString())

    @Test
    fun textPlainDate() = assertTextResult("/txt/date", Calendar.Builder().setDate(2023, 7, 4).build().toString())

    @Test
    fun textPlainPerson() = assertTextResult("/txt/person", Person("Dean Wette", 65).toString())

    private fun assertTextResult(url: String, expectedResult: String) {
        assertEquals(expectedResult, httpClient.toBlocking().retrieve(url))
    }
}

package io.micronaut.docs.http.server.stream

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StreamControllerSpec {

    lateinit var ctx: ApplicationContext
    lateinit var client: HttpClient

    @BeforeEach
    fun setup() {
        val server = ApplicationContext.run(
            EmbeddedServer::class.java,
            mapOf(
                "myapp.updatedAt" to mapOf( // <1>
                    "day" to 28,
                    "month" to 10,
                    "year" to 1982
                )
            )
        )
        ctx = server.applicationContext
        client = ctx.createBean(HttpClient::class.java, server.url)
    }

    @AfterEach
    fun teardown() {
        ctx.close()
    }


    @Test
    fun testReceivingAStream() {
        val response: String = client.toBlocking().retrieve(
            HttpRequest.GET<Any>("/stream/write"),
            String::class.java
        )

        Assertions.assertEquals("test", response)
    }

    @Test
    fun testReturningAStream() {
        val body = "My body"
        val response = client.toBlocking().retrieve(
                HttpRequest.POST("/stream/read", body)
                        .contentType(MediaType.TEXT_PLAIN_TYPE), String::class.java)

        Assertions.assertEquals(body, response)
    }

}

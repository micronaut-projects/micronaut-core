package io.micronaut.docs.taskexecutors

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.uri.UriBuilder
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.ThrowingSupplier
import java.util.Optional
import org.junit.jupiter.api.Assertions;

@Property(name = "spec.name", value = "TaskExecutorsBlockingTest")
@MicronautTest
class TaskExecutorsBlockingTest {
    @Inject
    @field:Client("/")
    lateinit var httpClient : HttpClient

    @Test
    fun testMethodAnnotatedWithTaskExecutorsBlocking() {
        val client = httpClient.toBlocking()
        val uri = UriBuilder.of("/hello").path("world").build()
        val request  = HttpRequest.GET<Any>(uri).accept(MediaType.TEXT_PLAIN)
        val response : HttpResponse<String> = client.exchange(request, String::class.java);
        Assertions.assertEquals(HttpStatus.OK, response.status());
        val txt = response.getBody(String::class.java)
        Assertions.assertTrue(txt.isPresent());
        Assertions.assertEquals("Hello World", txt.get());
    }
}

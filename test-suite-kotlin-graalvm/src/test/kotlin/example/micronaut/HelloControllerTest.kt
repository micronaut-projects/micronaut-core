package example.micronaut

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest
class HelloControllerTest {
    @Inject
    @Client("/")
    var client: HttpClient? = null

    @Test
    fun testHelloWorld1() {
        val response = client!!.toBlocking()
            .retrieve(HttpRequest.GET<Any>("/hello/world1"))
        Assertions.assertEquals("Hello World", response)
    }

    @Test
    fun testHelloWorld2() {
        val response = client!!.toBlocking()
            .retrieve(HttpRequest.GET<Any>("/hello/world2"))
        Assertions.assertEquals("Hello World", response)
    }
}

package io.micronaut.docs.client

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.jdk.DefaultJdkHttpClient
import io.micronaut.http.client.jdk.JdkHttpClient
import io.micronaut.http.client.netty.DefaultHttpClient
import io.micronaut.http.cookie.Cookie
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest
@Property(name = "spec.name", value = "MultiClientSpec")
class MultiClientSpec {

    @field:Client("/")
    @Inject
    lateinit var nettyClient: HttpClient

    @field:Client("/")
    @Inject
    lateinit var jdkClient: JdkHttpClient

    @Test
    fun testMultiClient() {
        assertEquals(DefaultHttpClient::class.java, nettyClient.javaClass)
        assertEquals(DefaultJdkHttpClient::class.java, jdkClient.javaClass)
        assertEquals("ok bar", nettyClient.toBlocking().retrieve(getRequest()))
        assertEquals("ok bar", jdkClient.toBlocking().retrieve(getRequest()))
    }

    private fun getRequest() = HttpRequest.GET<Any>("/multi-client").cookie(Cookie.of("foo", "bar"))

    @Controller
    @Requires(property = "spec.name", value = "MultiClientSpec")
    internal class MultiClientController {

        @Get(uri = "/multi-client", produces = [MediaType.TEXT_PLAIN])
        fun multiClient(@CookieValue("foo") foo: String) = "ok $foo"
    }
}

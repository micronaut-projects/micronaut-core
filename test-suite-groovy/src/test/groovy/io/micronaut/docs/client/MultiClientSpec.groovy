package io.micronaut.docs.client

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.jdk.DefaultJdkHttpClient
import io.micronaut.http.client.jdk.JdkHttpClient
import io.micronaut.http.client.netty.DefaultHttpClient
import io.micronaut.http.cookie.Cookie
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification


@MicronautTest
@Property(name = "spec.name", value = "MultiClientSpec")
class MultiClientSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient nettyClient

    @Inject
    @Client("/")
    JdkHttpClient jdkClient

    void "can specify client implementation if both are on the classpath"() {
        expect:
        nettyClient.class == DefaultHttpClient
        jdkClient.class == DefaultJdkHttpClient
        nettyClient.toBlocking().retrieve(getRequest()) == "ok bar"
        jdkClient.toBlocking().retrieve(getRequest()) == "ok bar"
    }

    private static MutableHttpRequest<Object> getRequest() {
        HttpRequest.GET("/multi-client").cookie(Cookie.of("foo", "bar"))
    }

    @Controller
    @Requires(property = "spec.name", value = "MultiClientSpec")
    static class MultiClientController {

        @Get(uri = "/multi-client", produces = MediaType.TEXT_PLAIN)
        String multiClient(@CookieValue("foo") String foo) {
            return "ok " + foo
        }
    }
}

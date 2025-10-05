package io.micronaut.http.client

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
@Property(name = 'micronaut.http.client.read-timeout', value = '30s')
@Property(name = 'micronaut.http.client.url-encoding', value = 'RFC_3986')
@Property(name = 'micronaut.router.url-decoding', value = 'RFC_3986')
@Property(name = 'spec.name', value = 'HttpGetRfc3986Spec')
class HttpGetRfc3986Spec  extends Specification {
    @Inject
    MyGetClient myGetClient

    @Inject
    @Client("/")
    HttpClient client

    void "test query parameter with @Client interface"() {
        expect:
        myGetClient.queryParam('Hello World') == 'Hello World'
        myGetClient.queryParam2('Hello World') == '/get/queryParam2?foo=Hello%20World'
    }


    void "test path decoding"() {
        given:
        HttpRequest<?> req = HttpRequest.GET("/rfc3986/1.0/" +
                "024-02-07T00:30:48.014+00:00?time=024-02-07T00:30:48.014+00:00");
        def result = client.toBlocking().retrieve(req)

        expect:"resolved values should using RFC-3986 decoding"
        // TODO: investigate why Netty QueryStringDecoder.parameters() doesn't respect RFC-3986
        result == 'resource variable path = 024-02-07T00:30:48.014+00:00,  from http req : path = /rfc3986/1.0/024-02-07T00:30:48.014+00:00,  from http req : query param time = 024-02-07T00:30:48.014 00:00'
    }

    @Controller
    @Requires(property = 'spec.name', value = 'HttpGetRfc3986Spec')
    static class HelloController {
        @Get("/get/queryParam")
        String queryParam(@QueryValue String foo) {
            return foo
        }

        @Get("/get/queryParam2")
        String queryParam2(HttpRequest<?> request) {
            return request.uri.toString()
        }

        @Get( "/rfc3986/{version}/{+resourcePath}" )
        @Produces(MediaType.ALL)
        HttpResponse<?> triggerGet(@PathVariable("version") @Nullable String version,
                                   @PathVariable("resourcePath") @Nullable String resourcePath,
                                   HttpRequest httpRequest) {

            String path = httpRequest.getPath() //This is the actual path
            return HttpResponse.ok("resource variable path = " + resourcePath + ", " +
                    " from http req : path = " + path + ", " +
                    " from http req : query param time = " + httpRequest.getParameters().get("time"))
        }
    }

    @Requires(property = 'spec.name', value = 'HttpGetRfc3986Spec')
    @Client("/get")
    static interface MyGetClient {
        @Get("/queryParam")
        String queryParam(@QueryValue String foo)

        @Get("/queryParam2")
        String queryParam2(@QueryValue String foo)
    }
}

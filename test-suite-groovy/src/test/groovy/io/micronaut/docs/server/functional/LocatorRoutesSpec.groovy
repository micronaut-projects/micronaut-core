package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class LocatorRoutesSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ["spec.name": "LocatorRoutesSpec"])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the routes of the located target answer under #prefix"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        expect:
        http.retrieve(HttpRequest.GET(prefix + "/north")) == "north"
        http.retrieve(HttpRequest.GET(prefix + "/north/items/1")) == "coffee"
        http.retrieve(HttpRequest.GET(prefix + "/south/items/0")) == "juice"
        status { http.retrieve(HttpRequest.GET(prefix + "/west/items/0")) } == HttpStatus.NOT_FOUND
        status { http.retrieve(HttpRequest.DELETE(prefix + "/north/items/0")) } == HttpStatus.METHOD_NOT_ALLOWED

        where:
        prefix << ["/shops", "/remote-shops"]
    }

    void "the routes the function chose for the located shop"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        expect:
        http.retrieve(HttpRequest.GET("/archived-shops/north/items/1")) == "coffee"
        http.retrieve(HttpRequest.GET("/archived-shops/south/item")) == "juice"
        status { http.retrieve(HttpRequest.GET("/archived-shops/south/items/0")) } == HttpStatus.NOT_FOUND
    }

    private static HttpStatus status(Closure<?> request) {
        try {
            request.call()
        } catch (HttpClientResponseException e) {
            return e.status
        }
        throw new AssertionError("the request did not fail")
    }
}

package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ItemRoutesSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ["spec.name": "ItemRoutesSpec"])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "items are created, read and deleted"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        when:
        HttpResponse<Item> created = http.exchange(HttpRequest.POST("/items", new Item(0, "pen")), Item)
        long id = created.body().id()

        then:
        created.status == HttpStatus.CREATED
        http.retrieve(HttpRequest.GET("/items/" + id), Item) == new Item(id, "pen")
        http.retrieve(HttpRequest.GET("/items/" + id + "/name")) == "pen"
        http.retrieve(HttpRequest.PATCH("/items/" + id + "/touch", "")) == "touched " + id + " with PATCH"
        http.retrieve(HttpRequest.PUT("/items/" + id + "/touch", "")) == "touched " + id + " with PUT"
        http.retrieve(HttpRequest.GET("/items/count"), Integer) >= 1
        http.exchange(HttpRequest.DELETE("/items/" + id)).status == HttpStatus.NO_CONTENT

        when:
        http.retrieve(HttpRequest.GET("/items/" + id))

        then:
        HttpClientResponseException notFound = thrown()
        notFound.status == HttpStatus.NOT_FOUND
    }

    void "a custom method and a nullable body"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        expect:
        http.retrieve(HttpRequest.create(HttpMethod.CUSTOM, "/items", "PROPFIND")) == "items: PROPFIND"
        http.exchange(HttpRequest.POST("/items/optional", null)
            .contentType(MediaType.APPLICATION_JSON_TYPE)).status == HttpStatus.NO_CONTENT
        http.exchange(HttpRequest.POST("/items/optional", new Item(0, "cup"))).status == HttpStatus.CREATED
    }

    void "an implicit HEAD route and a method not allowed"() {
        given:
        BlockingHttpClient http = client.toBlocking()
        Item item = http.retrieve(HttpRequest.POST("/items", new Item(0, "book")), Item)

        expect:
        http.exchange(HttpRequest.HEAD("/items/" + item.id())).status == HttpStatus.OK

        when:
        http.exchange(HttpRequest.PATCH("/items/" + item.id(), ""))

        then:
        HttpClientResponseException notAllowed = thrown()
        notAllowed.status == HttpStatus.METHOD_NOT_ALLOWED
    }
}

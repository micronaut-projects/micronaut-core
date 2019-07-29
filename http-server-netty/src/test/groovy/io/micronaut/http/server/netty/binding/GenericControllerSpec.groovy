package io.micronaut.http.server.netty.binding

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.server.netty.binding.generic.Status
import reactor.core.publisher.Mono
import spock.lang.Ignore

class GenericControllerSpec extends AbstractMicronautSpec {

    void "test saving a generic type in groovy"() {
        when:
        Status status = rxClient.retrieve(HttpRequest.POST("/statuses/groovy", '{"name":"Joe"}'), Status.class).blockingFirst()

        then:
        noExceptionThrown()
        status.name == "Joe"
    }

    void "test saving a generic type in java"() {
        when:
        Status status = rxClient.retrieve(HttpRequest.POST("/statuses/java", '{"name":"Joe"}'), Status.class).blockingFirst()

        then:
        noExceptionThrown()
        status.name == "Joe"
    }

    void "test with a generic as a path variable in groovy"() {
        when:
        Status status = rxClient.retrieve(HttpRequest.GET("/statuses/groovy/f1f654fd-ba75-4d2c-a98a-084fd1865b59"), Status.class).blockingFirst()

        then:
        noExceptionThrown()
        status.name == "status - f1f654fd-ba75-4d2c-a98a-084fd1865b59"
    }

    void "test with a generic as a path variable in java"() {
        when:
        Status status = rxClient.retrieve(HttpRequest.GET("/statuses/java/f1f654fd-ba75-4d2c-a98a-084fd1865b59"), Status.class).blockingFirst()

        then:
        noExceptionThrown()
        status.name == "status - f1f654fd-ba75-4d2c-a98a-084fd1865b59"
    }

    static abstract class GenericController<T, ID extends Serializable> {
        @Post
        Mono<HttpResponse<T>> save(@Body T entity) {
            assert entity instanceof Status
            Mono.just(HttpResponse.created((T)entity))
        }

        @Get("/{id}")
        Mono<HttpResponse<T>> find(ID id) {
            Mono.just(HttpResponse.created(create(id)))
        }

        abstract T create(ID id)
    }

    @Controller("/statuses/groovy")
    static class StatusController extends GenericController<Status, UUID> {

        Status create(UUID id) {
            new Status.StatusBuilder()
                    .withName("status - ${id.toString()}").build()
        }

    }

}

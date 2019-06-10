package io.micronaut.http.server.netty.binding

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.server.netty.binding.generic.Status
import reactor.core.publisher.Mono

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

    static abstract class GenericController<T, ID extends Serializable> {
        @Post
        Mono<HttpResponse<T>> save(@Body T entity) {
            Mono.just(HttpResponse.created(entity))
        }
    }

    @Controller("/statuses/groovy")
    static class StatusController extends GenericController<Status, UUID> {

    }

}

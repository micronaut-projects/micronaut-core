package io.micronaut.docs.basics

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.http.HttpRequest.GET
import static io.micronaut.http.HttpRequest.POST

class HelloControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(
            EmbeddedServer,
            ["spec.name": HelloControllerSpec.simpleName])
    @Shared @AutoCleanup HttpClient client = embeddedServer.applicationContext
                                                             .createBean(HttpClient, embeddedServer.URL)

    void "test simple retrieve"() {
        // tag::simple[]
        when:
        String uri = UriBuilder.of("/hello/{name}")
                               .expand(name: "John")
        then:
        "/hello/John" == uri

        when:
        String result = client.toBlocking().retrieve(uri)

        then:
        "Hello John" == result
        // end::simple[]
    }

    void "test retrieve with headers"() {
        when:
        // tag::headers[]
        Flux<String> response = Flux.from(client.retrieve(
                GET("/hello/John")
                .header("X-My-Header", "SomeValue")
        ))
        // end::headers[]

        then:
        "Hello John" == response.blockFirst()
    }

    void "test retrieve with JSON"() {
        when:
        // tag::jsonmap[]
        Flux<Map> response = Flux.from(client.retrieve(
                GET("/greet/John"), Map
        ))
        // end::jsonmap[]

        then:
        "Hello John" == response.blockFirst().get("text")

        when:
        // tag::jsonmaptypes[]
        response = Flux.from(client.retrieve(
                GET("/greet/John"),
                Argument.of(Map, String, String) // <1>
        ))
        // end::jsonmaptypes[]

        then:
        "Hello John" == response.blockFirst().get("text")
    }

    void "test retrieve with POJO"() {
        // tag::jsonpojo[]
        when:
        Flux<Message> response = Flux.from(client.retrieve(
                GET("/greet/John"), Message
        ))

        then:
        "Hello John" == response.blockFirst().getText()
        // end::jsonpojo[]
    }

    void "test retrieve with POJO response"() {
        // tag::pojoresponse[]
        when:
        Flux<HttpResponse<Message>> call = Flux.from(client.exchange(
                GET("/greet/John"), Message // <1>
        ))

        HttpResponse<Message> response = call.blockFirst();
        Optional<Message> message = response.getBody(Message) // <2>
        // check the status
        then:
        HttpStatus.OK == response.getStatus() // <3>
        // check the body
        message.isPresent()
        "Hello John" == message.get().getText()
        // end::pojoresponse[]
    }

    void "test post request with string"() {
        when:
        // tag::poststring[]
        Flux<HttpResponse<String>> call = Flux.from(client.exchange(
                POST("/hello", "Hello John") // <1>
                    .contentType(MediaType.TEXT_PLAIN_TYPE)
                    .accept(MediaType.TEXT_PLAIN_TYPE), // <2>
                String // <3>
        ))
        // end::poststring[]

        HttpResponse<String> response = call.blockFirst()
        Optional<String> message = response.getBody(String) // <2>
        // check the status
        then:
        HttpStatus.CREATED == response.getStatus() // <3>
        // check the body
        message.isPresent()
        "Hello John" == message.get()
    }

    void "test post request with POJO"() {
        when:
        // tag::postpojo[]
        Flux<HttpResponse<Message>> call = Flux.from(client.exchange(
                POST("/greet", new Message("Hello John")), // <1>
                Message // <2>
        ))
        // end::postpojo[]

        HttpResponse<Message> response = call.blockFirst()
        Optional<Message> message = response.getBody(Message) // <2>
        // check the status
        then:
        HttpStatus.CREATED == response.getStatus() // <3>
        // check the body
        message.isPresent()
        "Hello John" == message.get().getText()
    }
}

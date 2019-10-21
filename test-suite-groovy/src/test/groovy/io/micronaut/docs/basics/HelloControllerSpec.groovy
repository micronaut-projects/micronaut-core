package io.micronaut.docs.basics

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.http.HttpRequest.GET
import static io.micronaut.http.HttpRequest.POST

class HelloControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ["spec.name": HelloControllerSpec.simpleName])
    @Shared @AutoCleanup RxHttpClient client = embeddedServer.getApplicationContext()
                                                             .createBean(RxHttpClient, embeddedServer.getURL())

    void "test simple retrieve"() {
        // tag::simple[]
        when:
        String uri = UriBuilder.of("/hello/{name}")
                               .expand(Collections.singletonMap("name", "John"))
                               .toString()
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
        Flowable<String> response = client.retrieve(
                GET("/hello/John")
                .header("X-My-Header", "SomeValue")
        )
        // end::headers[]

        then:
        "Hello John" == response.blockingFirst()
    }

    void "test retrieve with JSON"() {
        when:
        // tag::jsonmap[]
        Flowable<Map> response = client.retrieve(
                GET("/greet/John"), Map.class
        )
        // end::jsonmap[]

        then:
        "Hello John" == response.blockingFirst().get("text")

        when:
        // tag::jsonmaptypes[]
        response = client.retrieve(
                GET("/greet/John"),
                Argument.of(Map.class, String.class, String.class) // <1>
        )
        // end::jsonmaptypes[]

        then:
        "Hello John" == response.blockingFirst().get("text")
    }

    void "test retrieve with POJO"() {
        // tag::jsonpojo[]
        when:
        Flowable<Message> response = client.retrieve(
                GET("/greet/John"), Message.class
        )

        then:
        "Hello John" == response.blockingFirst().getText()
        // end::jsonpojo[]
    }

    void "test retrieve with POJO response"() {
        // tag::pojoresponse[]
        when:
        Flowable<HttpResponse<Message>> call = client.exchange(
                GET("/greet/John"), Message.class // <1>
        )

        HttpResponse<Message> response = call.blockingFirst();
        Optional<Message> message = response.getBody(Message.class) // <2>
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
        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/hello", "Hello John") // <1>
                    .contentType(MediaType.TEXT_PLAIN_TYPE)
                    .accept(MediaType.TEXT_PLAIN_TYPE), // <2>
                String.class // <3>
        )
        // end::poststring[]

        HttpResponse<String> response = call.blockingFirst()
        Optional<String> message = response.getBody(String.class) // <2>
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
        Flowable<HttpResponse<Message>> call = client.exchange(
                POST("/greet", new Message("Hello John")), // <1>
                Message.class // <2>
        )
        // end::postpojo[]

        HttpResponse<Message> response = call.blockingFirst()
        Optional<Message> message = response.getBody(Message.class) // <2>
        // check the status
        then:
        HttpStatus.CREATED == response.getStatus() // <3>
        // check the body
        message.isPresent()
        "Hello John" == message.get().getText()
    }
}

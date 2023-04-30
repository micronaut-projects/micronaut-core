package io.micronaut.docs.basics

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest.GET
import io.micronaut.http.HttpRequest.POST
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import java.util.Collections

class HelloControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
        ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to HelloControllerSpec::class.simpleName))
    )

    val client = autoClose(
        embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url)
    )

    init {
        "test simple retrieve" {
            // tag::simple[]
            val uri = UriBuilder.of("/hello/{name}")
                                .expand(Collections.singletonMap("name", "John"))
                                .toString()
            uri shouldBe "/hello/John"

            val result = client.toBlocking().retrieve(uri)

            result shouldBe "Hello John"
            // end::simple[]
        }

        "test retrieve with headers" {
            // tag::headers[]
            val response = client.retrieve(
                    GET<Any>("/hello/John")
                            .header("X-My-Header", "SomeValue")
            )
            // end::headers[]

            Flux.from(response).blockFirst() shouldBe "Hello John"
        }

        "test retrieve with JSON" {
            // tag::jsonmap[]
            var response: Flux<Map<*, *>> = Flux.from(client.retrieve(
                    GET<Any>("/greet/John"), Map::class.java
            ))
            // end::jsonmap[]

            response.blockFirst()["text"] shouldBe "Hello John"

            // tag::jsonmaptypes[]
            response = Flux.from(client.retrieve(
                    GET<Any>("/greet/John"),
                    Argument.of(Map::class.java, String::class.java, String::class.java) // <1>
            ))
            // end::jsonmaptypes[]

            response.blockFirst()["text"] shouldBe "Hello John"
        }

        "test retrieve with POJO" {
            // tag::jsonpojo[]
            val response = Flux.from(client.retrieve(
                    GET<Any>("/greet/John"), Message::class.java
            ))

            response.blockFirst().text shouldBe "Hello John"
            // end::jsonpojo[]
        }

        "test retrieve with POJO response" {
            // tag::pojoresponse[]
            val call = client.exchange(
                    GET<Any>("/greet/John"), Message::class.java // <1>
            )

            val response = Flux.from(call).blockFirst()
            val message = response.getBody(Message::class.java) // <2>
            // check the status
            response.status shouldBe HttpStatus.OK // <3>
            // check the body
            message.isPresent shouldBe true
            message.get().text shouldBe "Hello John"
            // end::pojoresponse[]
        }

        "test post request with string" {
            // tag::poststring[]
            val call = client.exchange(
                    POST("/hello", "Hello John") // <1>
                            .contentType(MediaType.TEXT_PLAIN_TYPE)
                            .accept(MediaType.TEXT_PLAIN_TYPE), String::class.java // <3>
            )
            // end::poststring[]

            val response = Flux.from(call).blockFirst()
            val message = response.getBody(String::class.java) // <2>
            // check the status
            response.status shouldBe HttpStatus.CREATED // <3>
            // check the body
            message.isPresent shouldBe true
            message.get() shouldBe "Hello John"
        }

        "test post request with POJO" {
            // tag::postpojo[]
            val call = client.exchange(
                    POST("/greet", Message("Hello John")), Message::class.java // <2>
            )
            // end::postpojo[]

            val response = Flux.from(call).blockFirst()
            val message = response.getBody(Message::class.java) // <2>
            // check the status
            response.status shouldBe HttpStatus.CREATED // <3>
            // check the body
            message.isPresent shouldBe true
            message.get().text shouldBe "Hello John"
        }
    }
}

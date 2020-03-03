package io.micronaut.docs.basics

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable

import java.util.Collections

import io.micronaut.http.HttpRequest.GET
import io.micronaut.http.HttpRequest.POST

class HelloControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to HelloControllerSpec::class.simpleName))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.url)
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

            response.blockingFirst() shouldBe "Hello John"
        }

        "test retrieve with JSON" {
            // tag::jsonmap[]
            var response: Flowable<Map<*, *>> = client.retrieve(
                    GET<Any>("/greet/John"), Map::class.java
            )
            // end::jsonmap[]

            response.blockingFirst()["text"] shouldBe "Hello John"

            // tag::jsonmaptypes[]
            response = client.retrieve(
                    GET<Any>("/greet/John"),
                    Argument.of(Map::class.java, String::class.java, String::class.java) // <1>
            )
            // end::jsonmaptypes[]

            response.blockingFirst()["text"] shouldBe "Hello John"
        }

        "test retrieve with POJO" {
            // tag::jsonpojo[]
            val response = client.retrieve(
                    GET<Any>("/greet/John"), Message::class.java
            )

            response.blockingFirst().text shouldBe "Hello John"
            // end::jsonpojo[]
        }

        "test retrieve with POJO response" {
            // tag::pojoresponse[]
            val call = client.exchange(
                    GET<Any>("/greet/John"), Message::class.java // <1>
            )

            val response = call.blockingFirst()
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

            val response = call.blockingFirst()
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

            val response = call.blockingFirst()
            val message = response.getBody(Message::class.java) // <2>
            // check the status
            response.status shouldBe HttpStatus.CREATED // <3>
            // check the body
            message.isPresent shouldBe true
            message.get().text shouldBe "Hello John"
        }
    }
}

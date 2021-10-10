package io.micronaut.docs.basics

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import org.junit.Test

import java.util.Optional

import io.micronaut.http.HttpRequest.POST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class BookControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.url)
    )

    init {
        "test post with uri template" {
            // tag::posturitemplate[]
            val call = client.exchange(
                    POST("/amazon/book/{title}", Book("The Stand")),
                    Book::class.java
            )
            // end::posturitemplate[]

            val response = call.blockingFirst()
            val message = response.getBody(Book::class.java) // <2>
            // check the status
            response.status shouldBe HttpStatus.CREATED // <3>
            // check the body
            message.isPresent shouldBe true
            message.get().title shouldBe "The Stand"
        }

        "test post with form data" {
            // tag::postform[]
            val call = client.exchange(
                    POST("/amazon/book/{title}", Book("The Stand"))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED),
                    Book::class.java
            )
            // end::postform[]

            val response = call.blockingFirst()
            val message = response.getBody(Book::class.java) // <2>
            // check the status
            response.status shouldBe HttpStatus.CREATED // <3>
            // check the body
            message.isPresent shouldBe true
            message.get().title shouldBe "The Stand"
        }
    }
}

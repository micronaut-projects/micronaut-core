package io.micronaut.docs.server.consumes

import io.kotlintest.shouldNotThrowAny
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer

class ConsumesControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "consumesspec"))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test consumes"() {
            val book = Book()
            book.title = "The Stand"
            book.pages = 1000

            shouldThrow<HttpClientResponseException> {
                client.toBlocking().exchange<Book, Any>(HttpRequest.POST("/consumes", book)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))
            }

            shouldNotThrowAny {
                client.toBlocking().exchange<Book, Any>(HttpRequest.POST("/consumes", book)
                        .contentType(MediaType.APPLICATION_JSON))
            }

            shouldNotThrowAny {
                client.toBlocking().exchange<Book, Any>(HttpRequest.POST("/consumes/multiple", book)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))
            }

            shouldNotThrowAny {
                client.toBlocking().exchange<Book, Any>(HttpRequest.POST("/consumes/multiple", book)
                        .contentType(MediaType.APPLICATION_JSON))
            }

            shouldNotThrowAny {
                client.toBlocking().exchange<Book, Any>(HttpRequest.POST("/consumes/member", book)
                        .contentType(MediaType.TEXT_PLAIN))
            }
        }
    }

    @Introspected
    class Book {
        var title: String? = null
        var pages: Int? = null
    }
}

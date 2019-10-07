package io.micronaut.docs.server.response

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

import java.util.Collections

import org.junit.Assert.assertEquals

class ProducesControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "producesspec"))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test content types"() {
            var response = client.toBlocking().exchange(HttpRequest.GET<Any>("/produces"), String::class.java)

            response.contentType.get() shouldBe MediaType.APPLICATION_JSON_TYPE

            response = client.toBlocking().exchange(HttpRequest.GET<Any>("/produces/html"), String::class.java)

            response.contentType.get() shouldBe MediaType.TEXT_HTML_TYPE

            response = client.toBlocking().exchange(HttpRequest.GET<Any>("/produces/xml"), String::class.java)

            response.contentType.get() shouldBe MediaType.TEXT_XML_TYPE
        }
    }

}

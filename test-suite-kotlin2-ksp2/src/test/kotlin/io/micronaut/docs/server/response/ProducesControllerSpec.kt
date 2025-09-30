package io.micronaut.docs.server.response

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer

class ProducesControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "producesspec"))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.getURL())
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

package io.micronaut.docs.server.response

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer

class ContentDispositionControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "contentdisposition"))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test attachment with filename"() {
            val response = client.toBlocking().exchange(HttpRequest.GET<Any>("/content-disposition/report"), String::class.java)

            response.header(HttpHeaders.CONTENT_DISPOSITION) shouldBe "attachment; filename=\"report.csv\"; filename*=utf-8''report.csv"
        }

        "test inline"() {
            val response = client.toBlocking().exchange(HttpRequest.GET<Any>("/content-disposition/preview"), String::class.java)

            response.header(HttpHeaders.CONTENT_DISPOSITION) shouldBe "inline"
        }
    }

}

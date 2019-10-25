package io.micronaut.docs.server.exception

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

import java.util.Collections

import org.junit.Assert.assertEquals

class ExceptionHandlerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to ExceptionHandlerSpec::class.simpleName))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test exception is handled"() {
            val request = HttpRequest.GET<Any>("/books/stock/1234")
            val stock = client!!.toBlocking().retrieve(request, Long::class.javaPrimitiveType)

            stock shouldBe 0
        }
    }
}

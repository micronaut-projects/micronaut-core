package io.micronaut.docs.datavalidation.params

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.jupiter.api.Assertions

import java.util.Collections

import org.junit.Assert.assertEquals

class EmailControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "datavalidationparams"))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.getURL())
    )

    init {
        //tag::paramsvalidated[]
        "test params are validated"() {
            val e = shouldThrow<HttpClientResponseException> {
                client.toBlocking().exchange<Any>("/email/send?subject=Hi&recipient=")
            }
            var response = e.response

            response.status shouldBe HttpStatus.BAD_REQUEST

            response = client.toBlocking().exchange<Any>("/email/send?subject=Hi&recipient=me@micronaut.example")

            response.status shouldBe HttpStatus.OK
        }
        //end::paramsvalidated[]
    }
}

package io.micronaut.docs.server.endpoint

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.Assert
import org.junit.Test
import java.util.*

class AlertsEndpointSpec: StringSpec() {

    init {
        "test adding an alert" {
            var server = ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to AlertsEndpointSpec::class.simpleName))
            var client = server.applicationContext.createBean(RxHttpClient::class.java, server.url)
            try {
                client.exchange(HttpRequest.POST("/alerts", "First alert").contentType(MediaType.TEXT_PLAIN_TYPE), String::class.java).blockingFirst()
            } catch (ex: HttpClientResponseException) {
                ex.response.status() shouldBe HttpStatus.UNAUTHORIZED
            }
            server.close()
        }

        "test adding an alert not sensitive" {
            var server = ApplicationContext.run(EmbeddedServer::class.java,
                    mapOf("spec.name" to AlertsEndpointSpec::class.simpleName,
                            "endpoints.alerts.add.sensitive" to false)
            )
            var client = server.applicationContext.createBean(RxHttpClient::class.java, server.url)

            val response = client.exchange(HttpRequest.POST("/alerts", "First alert").contentType(MediaType.TEXT_PLAIN_TYPE), String::class.java).blockingFirst()
            response.status() shouldBe HttpStatus.OK

            val alerts = client.retrieve(HttpRequest.GET<Any>("/alerts"), Argument.LIST_OF_STRING).blockingFirst()
            alerts[0] shouldBe "First alert"

            server.close()
        }

        "test clearing alerts" {
            var server = ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to AlertsEndpointSpec::class.simpleName))
            var client = server.applicationContext.createBean(RxHttpClient::class.java, server.url)
            try {
                client.exchange(HttpRequest.DELETE<Any>("/alerts"), String::class.java).blockingFirst()
            } catch (ex: HttpClientResponseException) {
                ex.response.status() shouldBe HttpStatus.UNAUTHORIZED
            }
            server.close()
        }
    }

}
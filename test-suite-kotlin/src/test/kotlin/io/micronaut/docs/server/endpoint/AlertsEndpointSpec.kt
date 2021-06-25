package io.micronaut.docs.server.endpoint

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux

class AlertsEndpointSpec: StringSpec() {

    init {
        "test adding an alert" {
            var server = ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to AlertsEndpointSpec::class.simpleName))
            var client = server.applicationContext.createBean(HttpClient::class.java, server.url)
            try {
                Flux.from(client.exchange(HttpRequest.POST("/alerts", "First alert").contentType(MediaType.TEXT_PLAIN_TYPE), String::class.java)).blockFirst()
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
            var client = server.applicationContext.createBean(HttpClient::class.java, server.url)

            val response = Flux.from(client.exchange(HttpRequest.POST("/alerts", "First alert").contentType(MediaType.TEXT_PLAIN_TYPE), String::class.java)).blockFirst()
            response.status() shouldBe HttpStatus.OK

            val alerts = Flux.from(client.retrieve(HttpRequest.GET<Any>("/alerts"), Argument.LIST_OF_STRING)).blockFirst()
            alerts[0] shouldBe "First alert"

            server.close()
        }

        "test clearing alerts" {
            var server = ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to AlertsEndpointSpec::class.simpleName))
            var client = server.applicationContext.createBean(HttpClient::class.java, server.url)
            try {
                Flux.from(client.exchange(HttpRequest.DELETE<Any>("/alerts"), String::class.java)).blockFirst()
            } catch (ex: HttpClientResponseException) {
                ex.response.status() shouldBe HttpStatus.UNAUTHORIZED
            }
            server.close()
        }
    }

}
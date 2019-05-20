package io.micronaut.docs.context.annotation.primary

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer

class PrimarySpec : StringSpec() {

    val embeddedServer = autoClose(ApplicationContext.run(EmbeddedServer::class.java, mapOf(
            "spec.name" to "primaryspec"
    ), Environment.TEST))

    val rxClient = autoClose(embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.getURL()))

    init {
        "test @Primary annotated beans gets injected in case of a collection" {
            embeddedServer.applicationContext.getBeansOfType(ColorPicker::class.java).size.shouldBe(2)
            val rsp = rxClient.toBlocking().exchange(HttpRequest.GET<String>("/test"), String::class.java)

            rsp.status.shouldBe(HttpStatus.OK)
            rsp.body().shouldBe("green")
        }
    }
}
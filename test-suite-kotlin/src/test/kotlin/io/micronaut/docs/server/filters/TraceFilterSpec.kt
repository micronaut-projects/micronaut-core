package io.micronaut.docs.server.filters

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.docs.server.intro.HelloControllerSpec
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import java.util.HashMap


class TraceFilterSpec: StringSpec() {

    val map: HashMap<String, Any> = hashMapOf("spec.name" to HelloControllerSpec::class.java.simpleName, "spec.lang" to "java")

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java, map, Environment.TEST)
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test trace filter"() {
            val response = client.toBlocking().exchange<Any, Any>(HttpRequest.GET("/hello"))

            response.headers.get("X-Trace-Enabled") shouldBe "true"
        }
    }
}


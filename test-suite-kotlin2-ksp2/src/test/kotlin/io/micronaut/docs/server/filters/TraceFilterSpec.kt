package io.micronaut.docs.server.filters

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.docs.server.intro.HelloControllerSpec
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer

class TraceFilterSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java,
                    mapOf("spec.name" to HelloControllerSpec::class.java.simpleName, "spec.lang" to "java"))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url)
    )

    init {
        "test trace filter" {
            val response = client.toBlocking().exchange<Any, Any>(HttpRequest.GET("/hello"))

            response.headers.get("X-Trace-Enabled") shouldBe "true"
        }
    }
}


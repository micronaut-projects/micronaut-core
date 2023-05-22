package io.micronaut.docs.server.filters.filtermethods.continuations

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext
import io.micronaut.docs.server.intro.HelloControllerSpec
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer

class TraceFilterContinuationsSpec: StringSpec() {

    init {
        "test trace filter with continuations" {
            val embeddedServer = ApplicationContext.run(EmbeddedServer::class.java,
                mapOf("spec.name" to HelloControllerSpec::class.java.simpleName, "spec.filter" to "TraceFilterContinuation", "spec.lang" to "java"))
            val client = embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url)

            val response = client.toBlocking().exchange<Any, Any>(HttpRequest.GET("/hello"))

            response.headers.get("X-Trace-Enabled") shouldBe "true"

            embeddedServer.close()
            client.close()
        }
    }
}


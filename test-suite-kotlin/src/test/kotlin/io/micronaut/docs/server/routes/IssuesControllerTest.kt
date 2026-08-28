package io.micronaut.docs.server.routes

// tag::imports[]
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
// end::imports[]

// tag::class[]
class IssuesControllerTest: StringSpec() {

    val embeddedServer = autoClose( // <2>
        ApplicationContext.run(EmbeddedServer::class.java) // <1>
    )

    val client = autoClose( // <2>
        embeddedServer.applicationContext.createBean(
            HttpClient::class.java,
            embeddedServer.url) // <1>
    )

    init {
        "test issue" {
            val body = client.toBlocking().retrieve("/issues/12") // <3>

            body shouldNotBe null
            body shouldBe "Issue # 12!" // <4>
        }

        "test issue with invalid integer" {
            val e = shouldThrow<HttpClientResponseException> {
                client.toBlocking().exchange<Any>("/issues/hello")
            }

            e.status.code shouldBe 400 // <5>
        }

        "test issue without number" {
            val e = shouldThrow<HttpClientResponseException> {
                client.toBlocking().exchange<Any>("/issues/")
            }

            e.status.code shouldBe 404 // <6>
        }
    }
}
// end::class[]

package io.micronaut.docs.client.ssl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.ssl.ClientSslConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import java.net.URL
import javax.net.ssl.HostnameVerifier

class HostnameVerifierSpec : StringSpec() {

    private val server = autoClose(
        ApplicationContext.run(
            EmbeddedServer::class.java, mapOf(
                "spec.name" to "HostnameVerifierSpec",
                "micronaut.server.ssl.enabled" to true,
                "micronaut.server.ssl.build-self-signed" to true,
                "micronaut.server.ssl.port" to -1
            )
        )
    )

    init {
        "a verifier accepts the host" {
            client(server.url) { host, _ -> host == "localhost" }.use {
                it.toBlocking().retrieve("/verified") shouldBe "ok"
            }
        }

        "a rejecting verifier fails the request" {
            client(server.url) { _, _ -> false }.use {
                shouldThrow<Exception> { it.toBlocking().retrieve("/verified") }
            }
        }
    }

    private fun client(url: URL, verifier: HostnameVerifier): HttpClient {
        // tag::hostnameVerifier[]
        val configuration = DefaultHttpClientConfiguration()
        val ssl = ClientSslConfiguration()
        ssl.isInsecureTrustAllCertificates = true // only for the self-signed test certificate
        ssl.isEndpointIdentification = false
        ssl.hostnameVerifier = verifier // <1>
        configuration.setSslConfiguration(ssl)
        val client = HttpClient.create(url, configuration)
        // end::hostnameVerifier[]
        return client
    }
}

@Requires(property = "spec.name", value = "HostnameVerifierSpec")
@Controller
class VerifiedController {
    @Get("/verified")
    fun get() = "ok"
}

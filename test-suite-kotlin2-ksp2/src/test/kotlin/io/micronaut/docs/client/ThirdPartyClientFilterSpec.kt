package io.micronaut.docs.client

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import java.util.Base64
import jakarta.inject.Singleton
import reactor.core.publisher.Flux

class ThirdPartyClientFilterSpec: StringSpec() {
    private var result: String? = null
    private val token = "XXXX"
    private val username = "john"

    val embeddedServer = autoClose(
        ApplicationContext.run(EmbeddedServer::class.java,
            mapOf(
                "bintray.username" to username,
                "bintray.token" to token,
                "bintray.organization" to "grails",
                "spec.name" to ThirdPartyClientFilterSpec::class.simpleName))
    )

    val client = autoClose(
        embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url)
    )

    init {
        "a client filter is applied to the request and adds the authorization header" {
            val bintrayService = embeddedServer.applicationContext.getBean(BintrayService::class.java)

            result = bintrayService.fetchRepositories().blockFirst().body()

            val encoded = Base64.getEncoder().encodeToString("$username:$token".toByteArray())
            val expected = "Basic $encoded"

            result shouldBe expected
        }
    }

    @Controller("/repos")
    class HeaderController {

        @Get(value = "/grails")
        fun echoAuthorization(@Header authorization: String): String {
            return authorization
        }
    }
}

//tag::bintrayService[]
@Singleton
internal class BintrayService(
    @param:Client(BintrayApi.URL) val client: HttpClient, // <1>
    @param:Value("\${bintray.organization}") val org: String) {

    fun fetchRepositories(): Flux<HttpResponse<String>> {
        return Flux.from(client.exchange(HttpRequest.GET<Any>("/repos/$org"), String::class.java)) // <2>
    }

    fun fetchPackages(repo: String): Flux<HttpResponse<String>> {
        return Flux.from(client.exchange(HttpRequest.GET<Any>("/repos/$org/$repo/packages"), String::class.java)) // <2>
    }
}
//end::bintrayService[]

@Requires(property = "spec.name", value = "ThirdPartyClientFilterSpec")
//tag::bintrayFilter[]
@Filter("/repos/**") // <1>
internal class BintrayFilter(
        @param:Value("\${bintray.username}") val username: String, // <2>
        @param:Value("\${bintray.token}") val token: String)// <2>
    : HttpClientFilter {

    override fun doFilter(request: MutableHttpRequest<*>, chain: ClientFilterChain): Publisher<out HttpResponse<*>> {
        return chain.proceed(
            request.basicAuth(username, token) // <3>
        )
    }
}
//end::bintrayFilter[]

/*
//tag::bintrayApiConstants[]
class BintrayApi {
    public static final String URL = 'https://api.bintray.com'
}
//end::bintrayApiConstants[]
*/

internal object BintrayApi {
    const val URL = "/"
}

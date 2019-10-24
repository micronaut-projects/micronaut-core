package io.micronaut.docs.client

import io.kotlintest.eventually
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Value
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.reactivestreams.Publisher
import spock.lang.Retry

import javax.inject.Singleton
import java.util.Base64
import java.util.HashMap

import org.junit.Assert.assertEquals
import org.opentest4j.AssertionFailedError

@Retry
class ThirdPartyClientFilterSpec: StringSpec() {
    private var result: String? = null
    private val token = "XXXX"
    private val username = "john"

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java,
                    mapOf("micronaut.server.port" to SocketUtils.findAvailableTcpPort(),
                            "micronaut.http.clients.myService.url" to "http://localhost:\$port",
                            "bintray.username" to username, "bintray.token" to token, "bintray.organization" to "grails"))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.url)
    )

    init {
        "a client filter is applied to the request and adds the authorization header" {
            val bintrayService = embeddedServer.applicationContext.getBean(BintrayService::class.java)

//            bintrayService.fetchRepositories()
//                    .subscribe { str -> result = str.body() }
            result = bintrayService.fetchRepositories().blockingFirst().body()

            val encoded = Base64.getEncoder().encodeToString("$username:$token".toByteArray())
            val expected = "Basic $encoded"

//            eventually(10.seconds) {
//                result shouldNotBe null
//            }

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
        @param:Client(BintrayApi.URL) val client: RxHttpClient, // <1>
        @param:Value("\${bintray.organization}") val org: String) {

    fun fetchRepositories(): Flowable<HttpResponse<String>> {
        return client.exchange(HttpRequest.GET<Any>("/repos/$org"), String::class.java) // <2>
    }

    fun fetchPackages(repo: String): Flowable<HttpResponse<String>> {
        return client.exchange(HttpRequest.GET<Any>("/repos/$org/$repo/packages"), String::class.java) // <2>
    }
}
//end::bintrayService[]

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
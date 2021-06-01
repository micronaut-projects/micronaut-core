package io.micronaut.docs.client

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
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import jakarta.inject.Singleton

@Retry
class ThirdPartyClientFilterSpec extends Specification {

    private static final String token = 'XXXX'
    private static final String username = 'john'

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(
            EmbeddedServer, [
            'spec.name': ThirdPartyClientFilterSpec.simpleName,
            'bintray.username': username,
            'bintray.token': token,
            'bintray.organization': 'grails']
    ).applicationContext

    def "a client filter is applied to the request and adds the authorization header"() {
        given:
        def bintrayService = context.getBean(BintrayService)
        def conditions = new PollingConditions(timeout: 3, delay: 1)

        when:
        String result
        bintrayService.fetchRepositories().subscribe({ HttpResponse<String> str ->
            result = str.body()
        })

        String encoded = "$username:$token".bytes.encodeBase64()
        String expected = "Basic $encoded"

        then:
        conditions.eventually {
            assert result == expected
        }
    }

    @Controller('/repos')
    static class HeaderController {

        @Get(value = "/grails")
        String echoAuthorization(@Header String authorization) {
            authorization
        }
    }
}

//tag::bintrayService[]
@Singleton
class BintrayService {
    final RxHttpClient client
    final String org

    BintrayService(
            @Client(BintrayApi.URL) RxHttpClient client, // <1>
            @Value('${bintray.organization}') String org ) {
        this.client = client
        this.org = org
    }

    Flowable<HttpResponse<String>> fetchRepositories() {
        client.exchange(HttpRequest.GET("/repos/$org"), String) // <2>
    }

    Flowable<HttpResponse<String>> fetchPackages(String repo) {
        client.exchange(HttpRequest.GET("/repos/${org}/${repo}/packages"), String) // <2>
    }
}
//end::bintrayService[]

@Requires(property = "spec.name", value = "ThirdPartyClientFilterSpec")
//tag::bintrayFilter[]
@Filter('/repos/**') // <1>
class BintrayFilter implements HttpClientFilter {

    final String username
    final String token

    BintrayFilter(
            @Value('${bintray.username}') String username, // <2>
            @Value('${bintray.token}') String token ) { // <2>
        this.username = username
        this.token = token
    }

    @Override
    Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request,
                                                  ClientFilterChain chain) {
        chain.proceed(
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

class BintrayApi {
    public static final String URL = '/'
}

package io.micronaut.http.server.netty.errors

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.server.annotation.PreMatching
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Specification

@Retry
// Retry added because we need to use java.net.URL to test not the Micronaut HTTP client and URL.text from Groovy is unreliable
// sometimes failing for seemingly unknown reasons
class MalformedUriSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'MalformedUriSpec'
    ])
    @Shared @AutoCleanup HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "test malformed URI exceptions"() {
        when:
        def result = new URL("$embeddedServer.URL/malformed/[]").text

        then:
        result == 'Exception: Illegal character in path at index 11: /malformed/[]'
    }

    void "test filters are called in case of error"() {
        given:
        OncePerFilter filter = embeddedServer.applicationContext.getBean(OncePerFilter)

        expect:
        filter.filterCalled

        when:
        def result = new URL("$embeddedServer.URL/malformed/[]").text

        then:
        filter.filterCalled
        result == 'Exception: Illegal character in path at index 11: /malformed/[]'
    }

    void "test normal filter is not called for invalid uri"() {
        when:
        def result = new URL("$embeddedServer.URL/malformed-proxy/[]").text

        then:
        result == 'Exception: Illegal character in path at index 17: /malformed-proxy/[]'
    }

    void "header too long"() {
        given:
        OncePerFilter filter = embeddedServer.applicationContext.getBean(OncePerFilter)
        filter.filterCalled = false

        MalformedUriFilter newFilter = embeddedServer.applicationContext.getBean(MalformedUriFilter)
        newFilter.preMatchingCalled = false

        def connection = new URL("$embeddedServer.URL/malformed-proxy/xyz").openConnection()
        connection.setRequestProperty("foo", "b".repeat(9000))

        when:
        connection.inputStream
        then:
        thrown IOException

        when:
        def result = ((HttpURLConnection) connection).errorStream.text

        then:
        result == '{"message":"Request Entity Too Large","_links":{"self":{"href":"/malformed-proxy/xyz","templated":false}},"_embedded":{"errors":[{"message":"Request Entity Too Large"}]}}'
        !filter.filterCalled
        !newFilter.preMatchingCalled
    }

    @Requires(property = "spec.name", value = "MalformedUriSpec")
    @Controller('/malformed')
    static class SomeController {
        @Get(uri="/{some}", produces = MediaType.TEXT_PLAIN)
        String some(String some) throws Exception{
            return some
        }

        @Error(exception = URISyntaxException.class, global = true)
        HttpResponse<String> exception(HttpRequest request, URISyntaxException e) {
            return HttpResponse.<String>ok()
                    .body("Exception: " + e.getMessage())
        }
    }

    @Requires(property = "spec.name", value = "MalformedUriSpec")
    @Singleton
    @Filter("/**")
    static class OncePerFilter implements HttpServerFilter {

        boolean filterCalled = false

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            filterCalled = true
            return chain.proceed(request)
        }
    }

    @Requires(property = "spec.name", value = "MalformedUriSpec")
    @Singleton
    @ServerFilter("/malformed-proxy/**")
    static class MalformedUriFilter {
        boolean preMatchingCalled

        @RequestFilter
        HttpResponse<?> filter(HttpRequest<?> request) {
            return HttpResponse.ok("ok: " + request.path)
        }

        @RequestFilter
        @PreMatching
        void preMatching(HttpRequest<?> request) {
            preMatchingCalled = false
        }
    }
}

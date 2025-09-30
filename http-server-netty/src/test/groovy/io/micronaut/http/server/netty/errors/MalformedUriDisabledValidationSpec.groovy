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
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
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
class MalformedUriDisabledValidationSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'MalformedUriDisabledValidationSpec',
            'micronaut.server.escape-html-url': 'true'
    ])
    @Shared @AutoCleanup HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "test malformed URI exceptions"() {
        when:
        def result = new URL("$embeddedServer.URL/malformed/[]").text

        then:
        result == '[]'
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
        result == '[]'
    }

    @Requires(property = "spec.name", value = "MalformedUriDisabledValidationSpec")
    @Controller('/malformed')
    static class SomeController {
        @Get(uri="/{some}", produces = MediaType.TEXT_PLAIN)
        String some(HttpRequest<?> request, String some) throws Exception {
            assert request.uri.path.contains(some)
            return some
        }

        @Error(exception = URISyntaxException.class, global = true)
        HttpResponse<String> exception(HttpRequest request, URISyntaxException e) {
            return HttpResponse.<String>ok()
                    .body("Exception: " + e.getMessage())
        }
    }

    @Requires(property = "spec.name", value = "MalformedUriDisabledValidationSpec")
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
}

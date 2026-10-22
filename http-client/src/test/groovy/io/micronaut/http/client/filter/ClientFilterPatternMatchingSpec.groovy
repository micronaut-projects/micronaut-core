package io.micronaut.http.client.filter

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.FilterPatternStyle
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Pins which client filters run for the match-all pattern and for several regular expressions.
 * Client filters are sorted in reverse order, so the highest order runs first.
 */
class ClientFilterPatternMatchingSpec extends Specification {

    static final List<String> CALLS = new CopyOnWriteArrayList<>()

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ClientFilterPatternMatchingSpec'])

    @Shared
    @AutoCleanup
    ApplicationContext ctx = embeddedServer.applicationContext

    @Shared
    @AutoCleanup
    HttpClient client = ctx.createBean(HttpClient, embeddedServer.URL)

    def setup() {
        CALLS.clear()
    }

    void "filters with the match-all pattern and regular expressions apply by path: #method #path"() {
        when:
        client.toBlocking().retrieve(method == HttpMethod.POST ? HttpRequest.POST(path, "") : HttpRequest.GET(path))

        then:
        CALLS == expected

        when: 'repeated requests resolve the same way'
        CALLS.clear()
        client.toBlocking().retrieve(method == HttpMethod.POST ? HttpRequest.POST(path, "") : HttpRequest.GET(path))

        then:
        CALLS == expected

        where:
        method          | path           | expected
        HttpMethod.GET  | '/cfp/other'   | ['legacy-all', 'all']
        HttpMethod.GET  | '/cfp/a'       | ['legacy-all', 'all', 'regex']
        HttpMethod.GET  | '/cfp/a/x'     | ['legacy-all', 'all']
        HttpMethod.GET  | '/cfp/b/x'     | ['legacy-all', 'all', 'regex']
        HttpMethod.GET  | '/cfp/b'       | ['legacy-all', 'all']
        HttpMethod.POST | '/cfp/other'   | ['legacy-all', 'all', 'post-all']
        HttpMethod.POST | '/cfp/b/y'     | ['legacy-all', 'all', 'post-all', 'regex']
        HttpMethod.GET  | '/'            | ['legacy-all', 'all']
    }

    @Requires(property = 'spec.name', value = 'ClientFilterPatternMatchingSpec')
    @Controller
    static class CfpController {
        @Get('/{+path}')
        String get(String path) {
            'get'
        }

        @Get
        String root() {
            'get'
        }

        @Post('/{+path}')
        String post(String path) {
            'post'
        }
    }

    @Requires(property = 'spec.name', value = 'ClientFilterPatternMatchingSpec')
    @ClientFilter(patterns = ['/cfp/a', '/cfp/b/.*'], patternStyle = FilterPatternStyle.REGEX)
    @Order(10)
    static class RegexFilter {
        @RequestFilter
        void filter(MutableHttpRequest<?> request) {
            CALLS.add('regex')
        }
    }

    @Requires(property = 'spec.name', value = 'ClientFilterPatternMatchingSpec')
    @ClientFilter(value = ClientFilter.MATCH_ALL_PATTERN, methods = HttpMethod.POST)
    @Order(20)
    static class PostMatchAllFilter {
        @RequestFilter
        void filter(MutableHttpRequest<?> request) {
            CALLS.add('post-all')
        }
    }

    @Requires(property = 'spec.name', value = 'ClientFilterPatternMatchingSpec')
    @ClientFilter(ClientFilter.MATCH_ALL_PATTERN)
    @Order(30)
    static class MatchAllFilter {
        @RequestFilter
        void filter(MutableHttpRequest<?> request) {
            CALLS.add('all')
        }
    }

    @Requires(property = 'spec.name', value = 'ClientFilterPatternMatchingSpec')
    @Filter(Filter.MATCH_ALL_PATTERN)
    @Singleton
    static class LegacyMatchAllFilter implements HttpClientFilter {
        @Override
        int getOrder() {
            40
        }

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            CALLS.add('legacy-all')
            chain.proceed(request)
        }
    }
}

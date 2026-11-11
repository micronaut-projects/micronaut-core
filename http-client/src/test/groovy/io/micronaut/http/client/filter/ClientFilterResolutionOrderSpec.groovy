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
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Pins which client filters run, and in which order, for the different kinds of filter conditions.
 * Client filters are sorted in reverse order, so the highest order runs first.
 */
class ClientFilterResolutionOrderSpec extends Specification {

    static final List<String> CALLS = new CopyOnWriteArrayList<>()

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ClientFilterResolutionOrderSpec'])

    @Shared
    @AutoCleanup
    ApplicationContext ctx = embeddedServer.applicationContext

    def setup() {
        CALLS.clear()
    }

    void "a client without any filters runs none"() {
        given:
        def client = HttpClient.create(embeddedServer.URL)

        when:
        def body = client.toBlocking().retrieve('/cfr/pattern/x')

        then:
        body == 'get'
        CALLS.isEmpty()

        cleanup:
        client.close()
    }

    void "unconditional, pattern and legacy filters apply by path in order"() {
        given:
        def client = ctx.createBean(HttpClient, embeddedServer.URL)

        when:
        client.toBlocking().retrieve(path)

        then:
        CALLS == expected

        when: 'repeated requests resolve the same way'
        CALLS.clear()
        client.toBlocking().retrieve(path)

        then:
        CALLS == expected

        cleanup:
        client.close()

        where:
        path             | expected
        '/cfr/other'     | ['legacy', 'unconditional']
        '/cfr/pattern/x' | ['legacy', 'unconditional', 'pattern']
        '/cfr/other'     | ['legacy', 'unconditional']
    }

    void "method filters only apply to matching methods"() {
        given:
        def client = ctx.createBean(HttpClient, embeddedServer.URL)

        when:
        client.toBlocking().retrieve(HttpRequest.POST('/cfr/pattern/x', ''))

        then:
        CALLS == ['legacy', 'unconditional', 'method', 'pattern']

        when:
        CALLS.clear()
        client.toBlocking().retrieve(HttpRequest.GET('/cfr/pattern/x'))

        then:
        CALLS == ['legacy', 'unconditional', 'pattern']

        cleanup:
        client.close()
    }

    void "annotation bound filters only apply to annotated declarative clients"() {
        when:
        ctx.getBean(MarkedCfrClient).get()

        then:
        CALLS == ['legacy', 'annotation', 'unconditional', 'pattern']

        when:
        CALLS.clear()
        ctx.getBean(MarkedCfrClient).post()

        then:
        CALLS == ['legacy', 'annotation', 'unconditional', 'method', 'pattern']

        when:
        CALLS.clear()
        ctx.getBean(UnmarkedCfrClient).get()

        then:
        CALLS == ['legacy', 'unconditional', 'pattern']
    }

    @Requires(property = 'spec.name', value = 'ClientFilterResolutionOrderSpec')
    @Client('/cfr/pattern')
    @MarkerStereotypeAnnotation
    static interface MarkedCfrClient {
        @Get('/x')
        String get()

        @Post('/x')
        String post()
    }

    @Requires(property = 'spec.name', value = 'ClientFilterResolutionOrderSpec')
    @Client('/cfr/pattern')
    static interface UnmarkedCfrClient {
        @Get('/x')
        String get()
    }

    @Requires(property = 'spec.name', value = 'ClientFilterResolutionOrderSpec')
    @Controller('/cfr')
    static class CfrController {
        @Get('/{+path}')
        String get(String path) {
            'get'
        }

        @Post('/{+path}')
        String post(String path) {
            'post'
        }
    }

    @Requires(property = 'spec.name', value = 'ClientFilterResolutionOrderSpec')
    @ClientFilter('/cfr/pattern/**')
    @Order(10)
    static class PatternFilter {
        @RequestFilter
        void filter(MutableHttpRequest<?> request) {
            CALLS.add('pattern')
        }
    }

    @Requires(property = 'spec.name', value = 'ClientFilterResolutionOrderSpec')
    @ClientFilter(methods = HttpMethod.POST)
    @Order(20)
    static class MethodFilter {
        @RequestFilter
        void filter(MutableHttpRequest<?> request) {
            CALLS.add('method')
        }
    }

    @Requires(property = 'spec.name', value = 'ClientFilterResolutionOrderSpec')
    @ClientFilter
    @Order(30)
    static class UnconditionalFilter {
        @RequestFilter
        void filter(MutableHttpRequest<?> request) {
            CALLS.add('unconditional')
        }
    }

    @Requires(property = 'spec.name', value = 'ClientFilterResolutionOrderSpec')
    @ClientFilter
    @MarkerStereotypeAnnotation
    @Order(40)
    static class AnnotationFilter {
        @RequestFilter
        void filter(MutableHttpRequest<?> request) {
            CALLS.add('annotation')
        }
    }

    @Requires(property = 'spec.name', value = 'ClientFilterResolutionOrderSpec')
    @Filter('/cfr/**')
    @Singleton
    static class LegacyFilter implements HttpClientFilter {
        @Override
        int getOrder() {
            50
        }

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            CALLS.add('legacy')
            chain.proceed(request)
        }
    }
}

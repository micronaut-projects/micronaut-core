package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.core.util.StringUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.OncePerRequestHttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.Ignore
import spock.lang.PendingFeature
import spock.lang.Specification

class FilterErrorSpec extends Specification {

    void "test errors emitted from filters interacting with exception handlers"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': FilterErrorSpec.simpleName])
        def ctx = server.applicationContext
        RxHttpClient client = ctx.createBean(RxHttpClient, server.getURL())

        when:
        def response = client.exchange("/filter-error-spec", String)
                .onErrorReturn(t -> ((HttpClientResponseException)t).response)
                .blockingFirst()

        then:
        response.status() == HttpStatus.BAD_REQUEST
        response.body() == "from filter exception handler"

        when:
        response = client.exchange(HttpRequest.GET("/filter-error-spec").header("X-Passthru", "true"), String)
                .onErrorReturn(t -> ((HttpClientResponseException)t).response)
                .blockingFirst()

        then:
        response.status() == HttpStatus.BAD_REQUEST
        response.body() == "from NEXT filter exception handler"

        cleanup:
        client.close()
        ctx.close()
    }

    @Ignore
    void "test non once per request filter throwing error does not loop"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': FilterErrorSpec.simpleName + '2'])
        def ctx = server.applicationContext
        RxHttpClient client = ctx.createBean(RxHttpClient, server.getURL())

        when:
        def response = client.exchange("/filter-error-spec", String)
                .onErrorReturn(t -> ((HttpClientResponseException)t).response)
                .blockingFirst()

        then:
        response.status() == HttpStatus.BAD_REQUEST
        response.body() == "from filter exception handler"

        cleanup:
        client.close()
        ctx.close()
    }

    @Requires(property = 'spec.name', value = 'FilterErrorSpec')
    @Filter("/**")
    static class First extends OncePerRequestHttpServerFilter {

        @Override
        protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
            if (StringUtils.isTrue(request.getHeaders().get("X-Passthru"))) {
                return chain.proceed(request)
            }
            return Publishers.just(new FilterException())
        }

        @Override
        int getOrder() {
            10
        }
    }

    @Requires(property = 'spec.name', value = 'FilterErrorSpec')
    @Filter("/**")
    static class Next extends OncePerRequestHttpServerFilter {

        @Override
        protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
            return Publishers.just(new NextFilterException())
        }

        @Override
        int getOrder() {
            20
        }
    }

    @Requires(property = 'spec.name', value = 'FilterErrorSpec2')
    @Filter("/**")
    static class FirstEvery implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Publishers.just(new FilterException())
        }

        @Override
        int getOrder() {
            10
        }
    }

    @Controller("/filter-error-spec")
    static class NeverReachedController {

        @Get
        String get() {
            return "OK"
        }

    }
}

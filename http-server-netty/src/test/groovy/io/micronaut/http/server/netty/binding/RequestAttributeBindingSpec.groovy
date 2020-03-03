package io.micronaut.http.server.netty.binding

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.server.netty.AbstractMicronautSpec
import org.reactivestreams.Publisher
import spock.lang.Issue

import javax.annotation.Nullable

class RequestAttributeBindingSpec extends AbstractMicronautSpec {

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/2846")
    void "test request attribute binding from a filter"() {
        given:
        BlockingHttpClient client = rxClient.toBlocking()
        
        expect:
        client.retrieve("/attribute/filter/implicit") == "Sally"
        client.retrieve("/attribute/filter/implicit/nonnull") == "Sally"
        client.retrieve("/attribute/filter/annotation") == "Sally"
        client.retrieve("/attribute/filter/annotation/nonnull") == "Sally"

        when:
        client.retrieve("/attribute/filter/implicit?foo=false")

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.NOT_FOUND

        when:
        client.retrieve("/attribute/filter/implicit/nonnull?foo=false")

        then:
        ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.BAD_REQUEST

        when:
        client.retrieve("/attribute/filter/annotation?foo=false")

        then:
        ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.NOT_FOUND

        when:
        client.retrieve("/attribute/filter/annotation/nonnull?foo=false")

        then:
        ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.BAD_REQUEST
    }

    static class Foo {
        String name
    }

    @Requires(property = "spec.name", value = "RequestAttributeBindingSpec")
    @Controller("/attribute")
    static class MyController {

        @Get("/filter/implicit")
        String implicit(@Nullable Foo foo) {
            return foo?.name
        }

        @Get("/filter/implicit/nonnull")
        String implicitNotNull(Foo foo) {
            return foo?.name
        }

        @Get("/filter/annotation")
        String annotation(@RequestAttribute @Nullable Foo foo) {
            return foo?.name
        }

        @Get("/filter/annotation/nonnull")
        String annotationNotNull(@RequestAttribute Foo foo) {
            return foo?.name
        }
    }

    @Requires(property = "spec.name", value = "RequestAttributeBindingSpec")
    @Filter("/**")
    static class MyFilter implements HttpServerFilter {
        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            if (!request.getParameters().contains("foo")) {
                chain.proceed(request.setAttribute("foo", new Foo(name: "Sally")))
            } else {
                chain.proceed(request)
            }
        }
    }
}

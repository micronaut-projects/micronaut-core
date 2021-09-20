package io.micronaut.http.server.netty.context

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.lang.Unroll

class HttpClientFilterRequestInReactorContextSpec extends Specification {

    @Unroll
    void "HTTP Client filers can access original request via Reactor Context"(String path, String expected) {
        given:
        EmbeddedServer mockServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'HttpClientFilterRequestInReactorContextSpec.server'
        ])
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'HttpClientFilterRequestInReactorContextSpec',
                'micronaut.http.services.foo.url': "http://localhost:$mockServer.port",
        ])
        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)
        BlockingHttpClient client = httpClient.toBlocking()

        when:
        String response = client.retrieve(HttpRequest.GET(path).accept(MediaType.TEXT_PLAIN))

        then:
        expected == response

        cleanup:
        embeddedServer.close()
        mockServer.close()

        where:
        path            || expected
        '/barReactive'  || '/barReactive'
        '/bar'          || '/bar'
        '/foo'          || '/foo'
        '/fooReactive'  || '/fooReactive'
    }

    @Requires(property = 'spec.name', value = 'HttpClientFilterRequestInReactorContextSpec')
    @Filter("/foobar")
    static class FooHttpClientFilter implements HttpClientFilter {
        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            return Flux.deferContextual(contextView -> {
                HttpRequest<?> contexRequest = contextView.getOrDefault(ServerRequestContext.KEY, null);
                final String path = contexRequest == null ? "/nocontext" : contexRequest.getPath();
                return chain.proceed(request.header("FOOBAR", path));
            });
        }
    }

    @Requires(property = 'spec.name', value = 'HttpClientFilterRequestInReactorContextSpec')
    @Client(id = "foo")
    static interface FooClient {

        @Consumes(MediaType.TEXT_PLAIN)
        @Get("/foobar")
        @SingleResult
        Publisher<String> hi();

        @Consumes(MediaType.TEXT_PLAIN)
        @Get("/foobar")
        String hello();
    }

    @Requires(property = 'spec.name', value = 'HttpClientFilterRequestInReactorContextSpec')
    @Controller
    static class HomeController {

        private final FooClient fooClient

        HomeController(FooClient fooClient) {
            this.fooClient = fooClient
        }

        @Get("/fooReactive")
        @Produces(MediaType.TEXT_PLAIN)
        @SingleResult
        Publisher<String> fooReactive() {
            fooClient.hi()
        }

        @Get("/barReactive")
        @Produces(MediaType.TEXT_PLAIN)
        @SingleResult
        Publisher<String> barReactive() {
            fooClient.hi()
        }

        @Get("/foo")
        @Produces(MediaType.TEXT_PLAIN)
        @SingleResult
        Publisher<String> foo() {
            fooClient.hi()
        }

        @Get("/bar")
        @Produces(MediaType.TEXT_PLAIN)
        @SingleResult
        Publisher<String> bar() {
            fooClient.hi()
        }
    }

    @Requires(property = 'spec.name', value = 'HttpClientFilterRequestInReactorContextSpec.server')
    @Controller
    static class MockController {

        @Produces(MediaType.TEXT_PLAIN)
        @Get("/foobar")
        String index(@Nullable @Header("FOOBAR") customHeader) {
            customHeader
        }
    }
}

package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.OncePerRequestHttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.Specification
import spock.lang.Unroll

class UpdateRequestFilterSpec extends Specification {

    @Unroll
    void "test request update uri inside filter"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': UpdateRequestFilterSpec.simpleName])
        def applicationContext = server.applicationContext
        MyClient client = applicationContext.getBean(MyClient)

        when:
        def response = client.get()

        then:
        response == "OK"

        cleanup:
        server.close()
    }

    @Requires(property = 'spec.name', value = 'UpdateRequestFilterSpec')
    @Client("/")
    static interface MyClient {
        @Get("/test2")
        String get()
    }

    @Requires(property = 'spec.name', value = 'UpdateRequestFilterSpec')
    @Filter("/**")
    static class Filter1 extends OncePerRequestHttpServerFilter {

        @Override
        int getOrder() {
            return 1
        }

        @Override
        protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
            def newRequest = HttpRequest.create(HttpMethod.GET, "http://localhost:9090/test1")
            return chain.proceed(newRequest)
        }
    }

    @Requires(property = 'spec.name', value = 'UpdateRequestFilterSpec')
    @Filter("/**")
    static class Filter2 extends OncePerRequestHttpServerFilter {

        @Override
        int getOrder() {
            return 2
        }

        @Override
        protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {

            if (request.uri.toString() != "http://localhost:9090/test1") {
                throw new IllegalStateException("Uri is $request.uri but http://localhost:9090/test1 was expected")
            }

            return Publishers.just(HttpResponse.ok("OK"))
        }
    }
}

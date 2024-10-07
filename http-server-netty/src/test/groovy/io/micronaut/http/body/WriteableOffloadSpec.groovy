package io.micronaut.http.body

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.Writable
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification

@MicronautTest
@Property(name = "spec.name", value = "WriteableOffloadSpec")
class WriteableOffloadSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    void "writeable is offloaded"() {
        when:
        def result = client.toBlocking().retrieve("/writeable")

        then:
        result.startsWith(expectedThreadNamePrefix)
        result.endsWith("Hello")
    }

    String getExpectedThreadNamePrefix() {
        Runtime.version().feature() > 17 ? 'virtual-executor' : 'io-executor'
    }

    @SuppressWarnings('unused')
    @Requires(property = "spec.name", value = "WriteableOffloadSpec")
    @Filter(Filter.MATCH_ALL_PATTERN)
    static class WriteableOffloadFilter implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Flux.from(chain.proceed(request))
                    .map { response ->
                        response.contentType(MediaType.TEXT_PLAIN)
                        response.body(new ThreadWriteable(response.body()))
                    }
        }
    }

    @Requires(property = "spec.name", value = "WriteableOffloadSpec")
    @Controller
    static class WriteableOffloadController {

        @Get("/writeable")
        String get() {
            "Hello"
        }
    }


    static class ThreadWriteable<B> implements Writable {

        private final B body;

        ThreadWriteable(B body) {
            this.body = body;
        }

        @Override
        void writeTo(Writer out) throws IOException {
            out.write(Thread.currentThread().getName())
            out.write(" ")
            out.write(body.toString())
        }
    }
}

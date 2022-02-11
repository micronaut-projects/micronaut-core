package io.micronaut.http.server.netty.resources

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Filter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.FilterChain
import io.micronaut.http.filter.HttpFilter
import io.micronaut.http.server.netty.fuzzing.BufferLeakDetection
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification

import static io.micronaut.http.HttpHeaders.CONTENT_TYPE

class StaticResourceResolutionSpec2 extends Specification {

    void 'filter does not cause resource leak when used for file system resource'() {
        given:
        BufferLeakDetection.startTracking()

        def context = ApplicationContext.run(
                [
                        'micronaut.router.static-resources.default.paths': [
                                'classpath:public',
                                'file:' + StaticResourceResolutionSpec.tempFile.parent,
                                'file:' + StaticResourceResolutionSpec.tempSubDir.absolutePath
                        ],
                        'spec.name': 'StaticResourceResolutionSpec2'
                ]
        )
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URI)

        when:
        HttpResponse<String> response = Flux.from(client.exchange(
                HttpRequest.GET('/' + StaticResourceResolutionSpec.tempFile.getName()), String
        )).blockFirst()

        then:
        response.status == HttpStatus.OK
        response.header(CONTENT_TYPE) == "text/plain"
        response.body() == "discarded"

        BufferLeakDetection.stopTrackingAndReportLeaks()

        cleanup:
        client.close()
        server.stop()
    }

    @Requires(property = 'spec.name', value = 'StaticResourceResolutionSpec2')
    @Filter(Filter.MATCH_ALL_PATTERN)
    @Singleton
    static class DiscardingFilter implements HttpFilter {

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
            return Flux.from(chain.proceed(request)).map(resp -> {
                return HttpResponse.ok('discarded').contentType('text/plain')
            })
        }
    }
}

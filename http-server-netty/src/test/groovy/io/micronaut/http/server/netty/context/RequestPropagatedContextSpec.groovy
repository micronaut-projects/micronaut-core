package io.micronaut.http.server.netty.context

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.core.type.Argument
import io.micronaut.core.type.Headers
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.body.MessageBodyReader
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.codec.CodecException
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.netty.util.concurrent.FastThreadLocalThread
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * The server reuses the propagated context of a request when it already holds that request, so
 * these check that routes, filters, body binders and error handlers still see the current request
 * and the elements that filters added, on the event loop and on an executor.
 */
class RequestPropagatedContextSpec extends Specification {

    static final String SPEC = 'RequestPropagatedContextSpec'
    static final AtomicInteger IDS = new AtomicInteger()

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "route and filters see the request and user elements on #path"() {
        given:
        String id = "id-" + IDS.incrementAndGet()

        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET(path).header('X-Id', id), String)

        then:
        response.body() == "same=true,id=$id,trace=trace-$id,eventLoop=$eventLoop"
        response.header('X-Filter') == "same=true,id=$id,trace=trace-$id"

        where:
        path                           | eventLoop
        '/propagation/loop'            | true
        '/propagation/executor'        | false
        '/propagation/reactive'        | true
        '/propagation/reactive-executor' | false
    }

    void "body binder sees the request and user elements on #path"() {
        given:
        String id = "id-" + IDS.incrementAndGet()

        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.POST(path, 'payload')
            .contentType('application/x-probe')
            .header('X-Id', id), String)

        then:
        // the body is bound before the route runs, outside the context that the filter extended
        response.body() == "binder[same=true,id=$id,body=payload] route[same=true,id=$id,trace=trace-$id]"
        response.header('X-Filter') == "same=true,id=$id,trace=trace-$id"

        where:
        path << ['/propagation/body', '/propagation/body-executor']
    }

    void "error handler sees the request and user elements on #path"() {
        given:
        String id = "id-" + IDS.incrementAndGet()

        when:
        client.toBlocking().exchange(HttpRequest.GET(path).header('X-Id', id), String)

        then:
        HttpClientResponseException e = thrown()
        e.status.code == 409
        e.response.getBody(String).get() == "handler[same=true,id=$id,trace=trace-$id]"
        e.response.header('X-Filter') == "same=true,id=$id,trace=trace-$id"

        where:
        path << ['/propagation/error', '/propagation/error-executor']
    }

    void "a request nested inside another request's context gets its own request"() {
        given:
        String id = "id-" + IDS.incrementAndGet()

        when:
        String body = client.toBlocking().retrieve(HttpRequest.GET('/propagation/nested').header('X-Id', id), String)

        then:
        body == "inner=/nested-other,innerTrace=trace-$id,outer=same=true,id=$id,trace=trace-$id"
    }

    static String describe(HttpRequest<?> expected) {
        HttpRequest<?> current = ServerRequestContext.currentRequest().orElse(null)
        return "same=${current.is(expected)},id=${current?.headers?.get('X-Id')},trace=${trace()}"
    }

    static String trace() {
        return PropagatedContext.getOrEmpty().findOrNull(TraceElement)?.value
    }

    static boolean eventLoop() {
        return Thread.currentThread() instanceof FastThreadLocalThread && Thread.currentThread().name.toLowerCase(Locale.ROOT).contains('eventloop')
    }

    static class TraceElement implements PropagatedContextElement {
        final String value

        TraceElement(String value) {
            this.value = value
        }
    }

    static class Probe {
        final String value

        Probe(String value) {
            this.value = value
        }
    }

    static class ProbeException extends RuntimeException {
    }

    @Requires(property = 'spec.name', value = 'RequestPropagatedContextSpec')
    @ServerFilter('/propagation/**')
    static class TraceFilter {
        @RequestFilter
        void request(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
            if (!ServerRequestContext.currentRequest().orElse(null).is(request)) {
                throw new IllegalStateException('Filter does not see the request')
            }
            propagatedContext.add(new TraceElement('trace-' + request.headers.get('X-Id')))
        }

        @ResponseFilter
        void response(HttpRequest<?> request, MutableHttpResponse<?> response) {
            response.header('X-Filter', describe(request))
        }
    }

    @Requires(property = 'spec.name', value = 'RequestPropagatedContextSpec')
    @Singleton
    @Consumes('application/x-probe')
    static class ProbeReader implements MessageBodyReader<Probe> {
        @Override
        Probe read(Argument<Probe> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            HttpRequest<?> current = ServerRequestContext.currentRequest().orElse(null)
            boolean same = current != null && current.headers.get('X-Id') == httpHeaders.get('X-Id')
            return new Probe("binder[same=$same,id=${current?.headers?.get('X-Id')},body=${new String(inputStream.readAllBytes())}]")
        }
    }

    @Requires(property = 'spec.name', value = 'RequestPropagatedContextSpec')
    @Singleton
    static class ProbeExceptionHandler implements ExceptionHandler<ProbeException, HttpResponse<?>> {
        @Override
        HttpResponse<?> handle(HttpRequest request, ProbeException exception) {
            return HttpResponse.status(HttpStatus.CONFLICT).body("handler[${describe(request)}]".toString())
        }
    }

    @Requires(property = 'spec.name', value = 'RequestPropagatedContextSpec')
    @Controller('/propagation')
    @Produces(MediaType.TEXT_PLAIN)
    static class PropagationController {

        @Get('/loop')
        String loop(HttpRequest<?> request) {
            return describe(request) + ",eventLoop=${eventLoop()}"
        }

        @Get('/executor')
        @ExecuteOn(TaskExecutors.BLOCKING)
        String executor(HttpRequest<?> request) {
            return describe(request) + ",eventLoop=${eventLoop()}"
        }

        @Get('/reactive')
        Mono<String> reactive(HttpRequest<?> request) {
            return Mono.just(describe(request) + ",eventLoop=${eventLoop()}".toString())
        }

        @Get('/reactive-executor')
        @ExecuteOn(TaskExecutors.BLOCKING)
        Mono<String> reactiveExecutor(HttpRequest<?> request) {
            return Mono.just(describe(request) + ",eventLoop=${eventLoop()}".toString())
        }

        @Post(value = '/body', consumes = 'application/x-probe')
        String body(@Body Probe probe, HttpRequest<?> request) {
            return probe.value + " route[${describe(request)}]"
        }

        @Post(value = '/body-executor', consumes = 'application/x-probe')
        @ExecuteOn(TaskExecutors.BLOCKING)
        String bodyExecutor(@Body Probe probe, HttpRequest<?> request) {
            return probe.value + " route[${describe(request)}]"
        }

        @Get('/error')
        String error() {
            throw new ProbeException()
        }

        @Get('/error-executor')
        @ExecuteOn(TaskExecutors.BLOCKING)
        String errorExecutor() {
            throw new ProbeException()
        }

        @Get('/nested')
        String nested(HttpRequest<?> request) {
            String inner = ServerRequestContext.with(HttpRequest.GET('/nested-other'), {
                "inner=${ServerRequestContext.currentRequest().get().path},innerTrace=${trace()}".toString()
            } as Supplier<String>)
            return inner + ",outer=" + describe(request)
        }
    }
}

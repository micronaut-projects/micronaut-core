package io.micronaut.http.context

import io.micronaut.http.HttpRequest
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.function.Supplier

class ServerRequestContextSpec extends Specification {

    def "runnable is instrumented with request"() {
        given:
        HttpRequest request = HttpRequest.GET("/")

        when:
        Optional<HttpRequest> instrumentedRequest = null
        ServerRequestContext.with(request, {
            instrumentedRequest = ServerRequestContext.currentRequest()
        } as Runnable)

        then:
        instrumentedRequest.isPresent()
        instrumentedRequest.get() is request
        !ServerRequestContext.currentRequest().isPresent()
    }

    def "runnable instrumentation restores original request"() {
        given:
        HttpRequest firstRequest = HttpRequest.GET("/a")
        HttpRequest secondRequest = HttpRequest.GET("/b")

        when:
        Optional<HttpRequest> firstInstrumentedRequest = null
        Optional<HttpRequest> secondInstrumentedRequest = null
        Optional<HttpRequest> firstInstrumentedRequestRestored = null
        ServerRequestContext.with(firstRequest, {
            firstInstrumentedRequest = ServerRequestContext.currentRequest()
            ServerRequestContext.with(secondRequest, {
                secondInstrumentedRequest = ServerRequestContext.currentRequest()
            } as Runnable)
            firstInstrumentedRequestRestored = ServerRequestContext.currentRequest()
        } as Runnable)

        then:
        firstInstrumentedRequest.isPresent()
        firstInstrumentedRequest.get() is firstRequest
        secondInstrumentedRequest.isPresent()
        secondInstrumentedRequest.get() is secondRequest
        firstInstrumentedRequestRestored.isPresent()
        firstInstrumentedRequest.get() is firstRequest
        !ServerRequestContext.currentRequest().isPresent()
    }

    def "runnable instrumentation restores empty"() {
        given:
        HttpRequest secondRequest = HttpRequest.GET("/b")

        when:
        Optional<HttpRequest> firstInstrumentedRequest = null
        Optional<HttpRequest> secondInstrumentedRequest = null
        Optional<HttpRequest> firstInstrumentedRequestRestored = null
        ServerRequestContext.with(null, {
            firstInstrumentedRequest = ServerRequestContext.currentRequest()
            ServerRequestContext.with(secondRequest, {
                secondInstrumentedRequest = ServerRequestContext.currentRequest()
            } as Runnable)
            firstInstrumentedRequestRestored = ServerRequestContext.currentRequest()
        } as Runnable)

        then:
        !firstInstrumentedRequest.isPresent()
        secondInstrumentedRequest.isPresent()
        secondInstrumentedRequest.get() is secondRequest
        !firstInstrumentedRequestRestored.isPresent()
        !ServerRequestContext.currentRequest().isPresent()
    }

    def "runnable instrumentation overrides with empty"() {
        given:
        HttpRequest firstRequest = HttpRequest.GET("/a")

        when:
        Optional<HttpRequest> firstInstrumentedRequest = null
        Optional<HttpRequest> secondInstrumentedRequest = null
        Optional<HttpRequest> firstInstrumentedRequestRestored = null
        ServerRequestContext.with(firstRequest, {
            firstInstrumentedRequest = ServerRequestContext.currentRequest()
            ServerRequestContext.with(null, {
                secondInstrumentedRequest = ServerRequestContext.currentRequest()
            } as Runnable)
            firstInstrumentedRequestRestored = ServerRequestContext.currentRequest()
        } as Runnable)

        then:
        firstInstrumentedRequest.isPresent()
        firstInstrumentedRequest.get() is firstRequest
        !secondInstrumentedRequest.isPresent()
        firstInstrumentedRequestRestored.isPresent()
        firstInstrumentedRequest.get() is firstRequest
        !ServerRequestContext.currentRequest().isPresent()
    }

    def "supplier is instrumented with request"() {
        given:
        HttpRequest request = HttpRequest.GET("/")

        when:
        Optional<HttpRequest> instrumentedRequest = ServerRequestContext.with(request, {
            ServerRequestContext.currentRequest()
        } as Supplier<Optional<HttpRequest>>)

        then:
        instrumentedRequest.isPresent()
        instrumentedRequest.get() is request
        !ServerRequestContext.currentRequest().isPresent()
    }

    def "supplier instrumentation restores original request"() {
        given:
        HttpRequest firstRequest = HttpRequest.GET("/a")
        HttpRequest secondRequest = HttpRequest.GET("/b")

        when:
        Optional<HttpRequest> firstInstrumentedRequest = null
        Optional<HttpRequest> secondInstrumentedRequest = null
        Optional<HttpRequest> firstInstrumentedRequestRestored = null
        ServerRequestContext.with(firstRequest, {
            firstInstrumentedRequest = ServerRequestContext.currentRequest()
            ServerRequestContext.with(secondRequest, {
                secondInstrumentedRequest = ServerRequestContext.currentRequest()
            } as Supplier<Void>)
            firstInstrumentedRequestRestored = ServerRequestContext.currentRequest()
        } as Supplier<Void>)

        then:
        firstInstrumentedRequest.isPresent()
        firstInstrumentedRequest.get() is firstRequest
        secondInstrumentedRequest.isPresent()
        secondInstrumentedRequest.get() is secondRequest
        firstInstrumentedRequestRestored.isPresent()
        firstInstrumentedRequest.get() is firstRequest
        !ServerRequestContext.currentRequest().isPresent()
    }

    def "supplier instrumentation restores empty"() {
        given:
        HttpRequest secondRequest = HttpRequest.GET("/b")

        when:
        Optional<HttpRequest> firstInstrumentedRequest = null
        Optional<HttpRequest> secondInstrumentedRequest = null
        Optional<HttpRequest> firstInstrumentedRequestRestored = null
        ServerRequestContext.with(null, {
            firstInstrumentedRequest = ServerRequestContext.currentRequest()
            ServerRequestContext.with(secondRequest, {
                secondInstrumentedRequest = ServerRequestContext.currentRequest()
            } as Supplier<Void>)
            firstInstrumentedRequestRestored = ServerRequestContext.currentRequest()
        } as Supplier<Void>)

        then:
        !firstInstrumentedRequest.isPresent()
        secondInstrumentedRequest.isPresent()
        secondInstrumentedRequest.get() is secondRequest
        !firstInstrumentedRequestRestored.isPresent()
        !ServerRequestContext.currentRequest().isPresent()
    }

    def "supplier instrumentation overrides with empty"() {
        given:
        HttpRequest firstRequest = HttpRequest.GET("/a")

        when:
        Optional<HttpRequest> firstInstrumentedRequest = null
        Optional<HttpRequest> secondInstrumentedRequest = null
        Optional<HttpRequest> firstInstrumentedRequestRestored = null
        ServerRequestContext.with(firstRequest, {
            firstInstrumentedRequest = ServerRequestContext.currentRequest()
            ServerRequestContext.with(null, {
                secondInstrumentedRequest = ServerRequestContext.currentRequest()
            } as Supplier<Void>)
            firstInstrumentedRequestRestored = ServerRequestContext.currentRequest()
        } as Supplier<Void>)

        then:
        firstInstrumentedRequest.isPresent()
        firstInstrumentedRequest.get() is firstRequest
        !secondInstrumentedRequest.isPresent()
        firstInstrumentedRequestRestored.isPresent()
        firstInstrumentedRequest.get() is firstRequest
        !ServerRequestContext.currentRequest().isPresent()
    }

    def "callable is instrumented with request"() {
        given:
        HttpRequest request = HttpRequest.GET("/")

        when:
        Optional<HttpRequest> instrumentedRequest = ServerRequestContext.with(request, {
            ServerRequestContext.currentRequest()
        } as Callable<Optional<HttpRequest>>)

        then:
        instrumentedRequest.isPresent()
        instrumentedRequest.get() is request
        !ServerRequestContext.currentRequest().isPresent()
    }

    def "callable instrumentation restores original request"() {
        given:
        HttpRequest firstRequest = HttpRequest.GET("/a")
        HttpRequest secondRequest = HttpRequest.GET("/b")

        when:
        Optional<HttpRequest> firstInstrumentedRequest = null
        Optional<HttpRequest> secondInstrumentedRequest = null
        Optional<HttpRequest> firstInstrumentedRequestRestored = null
        ServerRequestContext.with(firstRequest, {
            firstInstrumentedRequest = ServerRequestContext.currentRequest()
            ServerRequestContext.with(secondRequest, {
                secondInstrumentedRequest = ServerRequestContext.currentRequest()
            } as Callable<Void>)
            firstInstrumentedRequestRestored = ServerRequestContext.currentRequest()
        } as Callable<Void>)

        then:
        firstInstrumentedRequest.isPresent()
        firstInstrumentedRequest.get() is firstRequest
        secondInstrumentedRequest.isPresent()
        secondInstrumentedRequest.get() is secondRequest
        firstInstrumentedRequestRestored.isPresent()
        firstInstrumentedRequest.get() is firstRequest
        !ServerRequestContext.currentRequest().isPresent()
    }

    def "callable instrumentation restores empty"() {
        given:
        HttpRequest secondRequest = HttpRequest.GET("/b")

        when:
        Optional<HttpRequest> firstInstrumentedRequest = null
        Optional<HttpRequest> secondInstrumentedRequest = null
        Optional<HttpRequest> firstInstrumentedRequestRestored = null
        ServerRequestContext.with(null, {
            firstInstrumentedRequest = ServerRequestContext.currentRequest()
            ServerRequestContext.with(secondRequest, {
                secondInstrumentedRequest = ServerRequestContext.currentRequest()
            } as Callable<Void>)
            firstInstrumentedRequestRestored = ServerRequestContext.currentRequest()
        } as Callable<Void>)

        then:
        !firstInstrumentedRequest.isPresent()
        secondInstrumentedRequest.isPresent()
        secondInstrumentedRequest.get() is secondRequest
        !firstInstrumentedRequestRestored.isPresent()
        !ServerRequestContext.currentRequest().isPresent()
    }

    def "callable instrumentation overrides with empty"() {
        given:
        HttpRequest firstRequest = HttpRequest.GET("/a")

        when:
        Optional<HttpRequest> firstInstrumentedRequest = null
        Optional<HttpRequest> secondInstrumentedRequest = null
        Optional<HttpRequest> firstInstrumentedRequestRestored = null
        ServerRequestContext.with(firstRequest, {
            firstInstrumentedRequest = ServerRequestContext.currentRequest()
            ServerRequestContext.with(null, {
                secondInstrumentedRequest = ServerRequestContext.currentRequest()
            } as Callable<Void>)
            firstInstrumentedRequestRestored = ServerRequestContext.currentRequest()
        } as Callable<Void>)

        then:
        firstInstrumentedRequest.isPresent()
        firstInstrumentedRequest.get() is firstRequest
        !secondInstrumentedRequest.isPresent()
        firstInstrumentedRequestRestored.isPresent()
        firstInstrumentedRequest.get() is firstRequest
        !ServerRequestContext.currentRequest().isPresent()
    }
}

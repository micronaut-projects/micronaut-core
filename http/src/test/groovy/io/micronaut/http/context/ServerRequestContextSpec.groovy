package io.micronaut.http.context

import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
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

    def "withRequest reuses a context that already holds the same request"() {
        given:
        HttpRequest request = HttpRequest.GET("/a")
        UserElement user = new UserElement("user")
        PropagatedContext context = PropagatedContext.empty().plus(new ServerHttpRequestContext(request)).plus(user)

        expect:
        ServerHttpRequestContext.withRequest(context, request).is(context)
        ServerHttpRequestContext.withRequest(context, request).getAllElements().size() == 2
    }

    def "withRequest adds the request when it differs from the current one"() {
        given:
        HttpRequest outer = HttpRequest.GET("/outer")
        HttpRequest inner = HttpRequest.GET("/inner")
        UserElement user = new UserElement("user")
        PropagatedContext context = PropagatedContext.empty().plus(new ServerHttpRequestContext(outer)).plus(user)

        when:
        PropagatedContext empty = ServerHttpRequestContext.withRequest(PropagatedContext.empty(), inner)
        PropagatedContext nested = ServerHttpRequestContext.withRequest(context, inner)

        then:
        ServerHttpRequestContext.find(empty).get().is(inner)
        !nested.is(context)
        ServerHttpRequestContext.find(nested).get().is(inner)
        nested.findOrNull(UserElement).is(user)
        ServerHttpRequestContext.find(context).get().is(outer)

        and: "an earlier element holding the request does not count once another request was added"
        !ServerHttpRequestContext.withRequest(nested, outer).is(nested)
        ServerHttpRequestContext.find(ServerHttpRequestContext.withRequest(nested, outer)).get().is(outer)
    }

    def "nested request context restores the outer request and keeps user elements"() {
        given:
        HttpRequest outer = HttpRequest.GET("/outer")
        HttpRequest inner = HttpRequest.GET("/inner")
        UserElement user = new UserElement("user")
        PropagatedContext context = PropagatedContext.empty().plus(new ServerHttpRequestContext(outer)).plus(user)

        when:
        List<Object> seen = context.propagate({
            List<Object> result = []
            ServerRequestContext.with(outer, {
                result << ServerRequestContext.currentRequest().get()
                result << PropagatedContext.get().is(context)
            } as Runnable)
            ServerRequestContext.with(inner, {
                result << ServerRequestContext.currentRequest().get()
                result << PropagatedContext.get().findOrNull(UserElement)
            } as Runnable)
            result << ServerRequestContext.currentRequest().get()
            result << PropagatedContext.get().is(context)
            return result
        } as Supplier<List<Object>>)

        then:
        seen[0].is(outer)
        seen[1] == true
        seen[2].is(inner)
        seen[3].is(user)
        seen[4].is(outer)
        seen[5] == true
        !PropagatedContext.exists()
    }

    def "scope of a reused context restores the previous context"() {
        given:
        HttpRequest request = HttpRequest.GET("/a")
        PropagatedContext context = PropagatedContext.empty().plus(new ServerHttpRequestContext(request))

        when:
        boolean restored
        try (PropagatedContext.Scope ignore = context.propagate()) {
            PropagatedContext same = ServerHttpRequestContext.withRequest(PropagatedContext.get(), request)
            try (PropagatedContext.Scope ignore2 = same.propagate()) {
                assert ServerRequestContext.currentRequest().get().is(request)
            }
            restored = PropagatedContext.get().is(context)
        }

        then:
        restored
        !PropagatedContext.exists()
    }

    static class UserElement implements PropagatedContextElement {
        final String value

        UserElement(String value) {
            this.value = value
        }
    }
}

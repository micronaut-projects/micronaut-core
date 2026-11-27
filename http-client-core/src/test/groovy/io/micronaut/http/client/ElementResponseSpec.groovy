package io.micronaut.http.client

import io.micronaut.http.HttpResponse
import io.micronaut.http.filter.FilterRunner
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList

class ElementResponseSpec extends Specification {

    def 'closing an element response releases what it holds once'() {
        given:
        int released = 0
        def response = new ElementResponse<>(HttpResponse.ok(), "element", { released++ })

        when:
        response.close()
        response.close()

        then:
        released == 1
    }

    def 'an element response discarded through composed discard hooks is released once'() {
        given: 'elements that can be subscribed to only once, as those of jsonStream'
        int subscriptions = 0
        def decoded = Flux.defer {
            if (subscriptions++ > 0) {
                return Flux.error(new IllegalStateException("The elements of a body can be subscribed to only once"))
            }
            return Flux.just("a")
        }
        def response = new ElementResponse<>(HttpResponse.ok(), decoded, { Flux.from(decoded).subscribe() })
        def dropped = new CopyOnWriteArrayList<Throwable>()
        Hooks.onErrorDropped { dropped.add(it) }

        when: 'the hook of toMono composes with the hook of a filter chain, as Reactor chains them'
        def result = Mono.just(response)
            .filter { false }
            .doOnDiscard(Object, FilterRunner::closeDiscarded)
            .doOnDiscard(Object, FilterRunner::closeDiscarded)
            .block()

        then:
        result == null
        subscriptions == 1
        dropped.isEmpty()

        cleanup:
        Hooks.resetOnErrorDropped()
    }
}

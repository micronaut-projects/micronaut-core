package io.micronaut.core.async.publisher

import io.micronaut.core.convert.ConversionService
import org.reactivestreams.FlowAdapters
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher

class FlowPublisherSpec extends Specification {

    void "Flow.Publisher is convertible to a publisher"() {
        expect:
        Publishers.isConvertibleToPublisher(Flow.Publisher)
        Publishers.isConvertibleToPublisher(SubmissionPublisher)
        Publishers.isConvertibleToPublisher(FlowAdapters.toFlowPublisher(Flux.just(1)))
        !Publishers.isConvertibleToPublisher(String)
        !Publishers.isConvertibleToPublisher(List)
        !Publishers.isConvertibleToPublisher(Flow.Subscriber)
    }

    void "a Flow.Publisher converts to a reactive streams publisher and back"() {
        given:
        Flow.Publisher<Integer> flow = FlowAdapters.toFlowPublisher(Flux.just(1, 2))

        when:
        Publisher<Integer> publisher = Publishers.convertToPublisher(ConversionService.SHARED, flow)

        then:
        Flux.from(publisher).collectList().block() == [1, 2]

        when:
        Flow.Publisher<Integer> back = Publishers.convertPublisher(ConversionService.SHARED, Flux.just(3, 4), Flow.Publisher)

        then:
        Flux.from(FlowAdapters.toPublisher(back)).collectList().block() == [3, 4]

        when:
        Publisher<Integer> converted = ConversionService.SHARED.convert(flow, Publisher).get()

        then:
        Flux.from(converted).collectList().block() == [1, 2]
    }
}

package io.micronaut.aop.async

import io.micronaut.aop.InterceptedMethod
import io.micronaut.context.ApplicationContext
import org.reactivestreams.FlowAdapters
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class FlowPublisherInterceptionSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    void "a method returning Flow.Publisher is intercepted as a publisher"() {
        given:
        AsyncBean bean = context.getBean(AsyncBean)
        AsyncObservedInterceptor interceptor = context.getBean(AsyncObservedInterceptor)

        when:
        def result = bean.flowPublisher()

        then:
        interceptor.resultTypes.flowPublisher == InterceptedMethod.ResultType.PUBLISHER
        !interceptor.completed.contains("flowPublisher")

        when:
        def items = Flux.from(FlowAdapters.toPublisher(result)).collectList().block()

        then:
        items == ["a", "b"]
        interceptor.completed.contains("flowPublisher")
    }

    void "the other return types keep their result type"() {
        given:
        AsyncBean bean = context.getBean(AsyncBean)
        AsyncObservedInterceptor interceptor = context.getBean(AsyncObservedInterceptor)

        when:
        bean.reactiveStreamsPublisher()
        bean.string()

        then:
        interceptor.resultTypes.reactiveStreamsPublisher == InterceptedMethod.ResultType.PUBLISHER
        interceptor.resultTypes.string == InterceptedMethod.ResultType.SYNCHRONOUS
    }

    void "the propagated context of the interceptor reaches a Flow.Publisher"() {
        given:
        PropagatingBean bean = context.getBean(PropagatingBean)

        expect:
        Flux.from(FlowAdapters.toPublisher(bean.flowPublisher())).blockFirst() == "marked"
        bean.flux().blockFirst() == "marked"
    }
}

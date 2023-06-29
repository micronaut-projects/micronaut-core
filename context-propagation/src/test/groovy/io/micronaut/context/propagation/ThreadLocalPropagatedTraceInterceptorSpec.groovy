package io.micronaut.context.propagation

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ThreadLocalPropagatedTraceInterceptorSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    void "test trace mono"() {
        when:
            TracedService tracedService = embeddedServer.getApplicationContext().getBean(TracedService)
            tracedService.tracingInterceptor.traces.clear()
            String result = tracedService.mono("test").block()
            PollingConditions conditions = new PollingConditions(timeout: 3)

        then:
            conditions.eventually {
                result == "test"
                def traces = tracedService.tracingInterceptor.traces
                traces[0].tags().get("fooMono") == "bar"
            }
    }

    void "test trace flux"() {
        when:
            TracedService tracedService = embeddedServer.getApplicationContext().getBean(TracedService)
            tracedService.tracingInterceptor.traces.clear()
            String result = tracedService.flux("test").collectList().block()
            PollingConditions conditions = new PollingConditions(timeout: 3)

        then:
            conditions.eventually {
                def traces = tracedService.tracingInterceptor.traces
                traces[0].tags().get("fooFlux") == "bar"
            }
    }

    @Singleton
    static class TracedService {

        @Inject
        MyTracingInterceptor tracingInterceptor

        @MyTrace
        Mono<String> mono(String name) {
            return Mono.fromCallable({
                tracingInterceptor.getCurrentTrace().tag("fooMono", "bar")
                return name
            }).subscribeOn(Schedulers.boundedElastic())
        }

        @MyTrace
        Flux<String> flux(String name) {
            return Mono.fromCallable {
                tracingInterceptor.getCurrentTrace().tag("fooFlux", "bar")
                return name
            }.flatMapMany { x -> Flux.just(x) }.subscribeOn(Schedulers.boundedElastic())
        }
    }

}

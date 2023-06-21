package io.micronaut.context.propagation

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class SimpleTraceInterceptorSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ["reactor.enableAutomaticContextPropagation": false])

    void "test trace mono"() {
        when:
            TracedService tracedService = embeddedServer.getApplicationContext().getBean(TracedService)
            String result = tracedService.mono("test").block()
            PollingConditions conditions = new PollingConditions(timeout: 3)

        then:
            conditions.eventually {
                result == "test"
                def traces = tracedService.tracingInterceptor.traces
                traces[0].tags().get("foo") == "bar"
            }
    }

    @Singleton
    static class TracedService {

        @Inject
        MyTracingInterceptor tracingInterceptor

        @MyTrace
        Mono<String> mono(String name) {
            def trace = tracingInterceptor.getCurrentTrace()
            return Mono.fromCallable({
                if (tracingInterceptor.findCurrentTrace().isPresent()) {
                    throw new IllegalStateException("Not expected trace")
                }
                trace.tag("foo", "bar")
                return name
            }).subscribeOn(Schedulers.boundedElastic())
        }
    }

}

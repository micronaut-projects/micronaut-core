package io.micronaut.configuration.metrics.annotation

import io.micrometer.core.annotation.Timed
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import io.reactivex.Single
import spock.lang.Specification

import javax.inject.Singleton
import java.util.concurrent.CompletableFuture

class TimeAnnotationSpec extends Specification {

    void "test timed annotation usage"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        TimedTarget tt = ctx.getBean(TimedTarget)
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        def result = tt.max(4, 10)

        then:
        result == 10
        registry.get("max.blocking")


        cleanup:
        ctx.close()
    }


    @Singleton
    static class TimedTarget {

        @Timed("max.blocking")
        Integer max(int a, int b) {
            return Math.max(a, b)
        }

        @Timed(value = "max.future", description = "some desc", extraTags = ['one', 'two'])
        CompletableFuture<Integer> maxFuture(int a, int b) {
            CompletableFuture.completedFuture(
                Math.max(a, b)
            )
        }

        @Timed(value = "max.rx", description = "some desc", extraTags = ['one', 'two'])
        Single<Integer> maxRx(int a, int b) {
            Single.just(
                    Math.max(a, b)
            )
        }
    }
}

package io.micronaut.configuration.metrics.annotation

import io.micrometer.core.annotation.Timed
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.functions.Consumer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.inject.Singleton
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class TimeAnnotationSpec extends Specification {

    void "test timed annotation usage"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        TimedTarget tt = ctx.getBean(TimedTarget)
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        def result = tt.max(4, 10)
        def timer = registry.get("timed.test.max.blocking").timer()

        then:
        result == 10
        timer.count() == 1
        timer.totalTime(TimeUnit.MILLISECONDS) > 0

        when:
        result = tt.maxFuture(4, 10).get()
        PollingConditions conditions = new PollingConditions()


        then:
        conditions.eventually {
            def t = registry.get("timed.test.max.future").timer()
            result == 10
            t.count() == 1
            t.totalTime(TimeUnit.MILLISECONDS) > 0

        }
        timer.count() == 1

        when:
        tt.maxSingle(4, 10).subscribe( { o -> result = o} as Consumer)

        then:
        conditions.eventually {
            def rxTimer = registry.get("timed.test.max.single").timer()

            result == 10
            rxTimer.count() == 1
            rxTimer.totalTime(TimeUnit.MILLISECONDS) > 0
        }

        when:
        tt.maxFlow(4, 10).toList().subscribe( { o -> result = o[0]} as Consumer)

        then:
        conditions.eventually {
            def rxTimer = registry.get("timed.test.max.flowable").timer()

            result == 10
            rxTimer.count() == 1
            rxTimer.totalTime(TimeUnit.MILLISECONDS) > 0
        }


        cleanup:
        ctx.close()
    }


    @Singleton
    static class TimedTarget {

        @Timed("timed.test.max.blocking")
        Integer max(int a, int b) {
            return Math.max(a, b)
        }

        @Timed("timed.test.max.blocking")
        Integer error(int a, int b) {
            throw new NumberFormatException("cannot")
        }


        @Timed(value = "timed.test.max.future", description = "some desc", extraTags = ['one', 'two'])
        CompletableFuture<Integer> maxFuture(int a, int b) {
            CompletableFuture.completedFuture(
                Math.max(a, b)
            )
        }

        @Timed(value = "timed.test.max.single", description = "some desc", extraTags = ['one', 'two'])
        Single<Integer> maxSingle(int a, int b) {
            Single.just(
                    Math.max(a, b)
            )
        }

        @Timed(value = "timed.test.max.flowable", description = "some desc", extraTags = ['one', 'two'])
        Flowable<Integer> maxFlow(int a, int b) {
            Flowable.just(
                    Math.max(a, b)
            )
        }
    }
}

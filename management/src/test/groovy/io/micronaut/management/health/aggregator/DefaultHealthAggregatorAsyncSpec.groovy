package io.micronaut.management.health.aggregator

import io.micronaut.context.ApplicationContext
import io.micronaut.core.async.publisher.CompletionStagePublishers
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.health.HealthStatus
import io.micronaut.management.endpoint.health.HealthLevelOfDetail
import io.micronaut.management.health.indicator.HealthIndicator
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.runtime.ApplicationConfiguration
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DefaultHealthAggregatorAsyncSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(['micronaut.application.name': 'foo'])

    @Shared
    DefaultHealthAggregator aggregator = new AsyncHealthAggregator(context.getBean(ApplicationConfiguration))

    void 'aggregateAsync combines the async results and the publisher-only indicators'() {
        given:
        def futureA = new CompletableFuture<HealthResult>()
        HealthIndicator[] indicators = [
                new AsyncIndicator(futureA),
                new PublisherOnlyIndicator(Flux.just(HealthResult.builder('b', HealthStatus.UP).details([x: 1]).build())),
                new PublisherOnlyIndicator(Publishers.empty()),
                new AsyncIndicator(CompletableFuture.completedFuture(HealthResult.builder('c', HealthStatus.UP).build()))
        ]

        when:
        def stage = aggregator.aggregateAsync(indicators, HealthLevelOfDetail.STATUS_DESCRIPTION_DETAILS).toCompletableFuture()

        then:
        !stage.done

        when:
        futureA.complete(HealthResult.builder('a', HealthStatus.DOWN).build())
        HealthResult result = stage.get(5, TimeUnit.SECONDS)

        then:
        result.name == 'foo'
        result.status == HealthStatus.DOWN
        (result.details as Map).keySet() == ['a', 'b', 'c'] as Set
        result.details.a.status == HealthStatus.DOWN
        result.details.b.status == HealthStatus.UP
        result.details.b.details == [x: 1]
        result.details.c.status == HealthStatus.UP
    }

    void 'aggregateResultsAsync keeps the order of the indicators, not of completion'() {
        given:
        def first = new CompletableFuture<HealthResult>()
        def second = new CompletableFuture<HealthResult>()
        HealthIndicator[] indicators = [new AsyncIndicator(first), new AsyncIndicator(second)]

        when:
        def stage = aggregator.aggregateResultsAsync(indicators).toCompletableFuture()
        second.complete(HealthResult.builder('second', HealthStatus.UP).build())
        first.complete(HealthResult.builder('first', HealthStatus.UP).build())

        then:
        stage.get(5, TimeUnit.SECONDS)*.name == ['first', 'second']
    }

    void 'the overall status is the most severe status, or UNKNOWN without results'() {
        expect:
        aggregator.aggregateAsync(indicators(statuses), HealthLevelOfDetail.STATUS)
                .toCompletableFuture().get(5, TimeUnit.SECONDS).status == expected

        where:
        statuses                                                   | expected
        []                                                         | HealthStatus.UNKNOWN
        [HealthStatus.UP]                                          | HealthStatus.UP
        [HealthStatus.UP, HealthStatus.UNKNOWN]                    | HealthStatus.UNKNOWN
        [HealthStatus.UP, HealthStatus.DOWN, HealthStatus.UNKNOWN] | HealthStatus.DOWN
    }

    void 'the STATUS level of detail omits the name and the details'() {
        when:
        HealthResult result = aggregator.aggregateAsync(indicators([HealthStatus.UP]), HealthLevelOfDetail.STATUS)
                .toCompletableFuture().get(5, TimeUnit.SECONDS)

        then:
        result.name == null
        result.details == null
        result.status == HealthStatus.UP
    }

    void 'the first failing indicator fails the aggregation and cancels the others'() {
        given:
        def error = new IllegalStateException('boom')
        def pending = new CompletableFuture<HealthResult>()
        def cancelled = new AtomicBoolean()
        HealthIndicator[] indicators = [
                new AsyncIndicator(pending),
                new PublisherOnlyIndicator(Mono.<HealthResult> never().doOnCancel { cancelled.set(true) }),
                new AsyncIndicator(CompletableFuture.failedFuture(error))
        ]

        when:
        aggregator.aggregateAsync(indicators, HealthLevelOfDetail.STATUS_DESCRIPTION_DETAILS).toCompletableFuture().get(5, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause.is(error)
        pending.cancelled
        cancelled.get()
    }

    void 'an indicator that throws fails the aggregation'() {
        given:
        def error = new IllegalStateException('thrown')
        HealthIndicator[] indicators = [new ThrowingIndicator(error)]

        when:
        aggregator.aggregateAsync(indicators, HealthLevelOfDetail.STATUS).toCompletableFuture().get(5, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause.is(error)

        when:
        Mono.from(aggregator.aggregate(indicators, HealthLevelOfDetail.STATUS)).block()

        then:
        def e2 = thrown(IllegalStateException)
        e2.is(error)
    }

    void 'cancelling the aggregation cancels the indicators'() {
        given:
        def pending = new CompletableFuture<HealthResult>()
        HealthIndicator[] indicators = [new AsyncIndicator(pending)]

        when:
        aggregator.aggregateAsync(indicators, HealthLevelOfDetail.STATUS).toCompletableFuture().cancel(false)

        then:
        pending.cancelled
    }

    void 'aggregate emits the result of aggregateAsync and calls the indicators on subscription'() {
        given:
        def calls = new AtomicInteger()
        HealthIndicator[] indicators = [new AsyncIndicator(null) {
            @Override
            CompletionStage<List<HealthResult>> getResultAsync() {
                calls.incrementAndGet()
                return CompletableFuture.completedFuture([HealthResult.builder('a', HealthStatus.UP).build()])
            }
        }]

        when:
        Publisher<HealthResult> publisher = aggregator.aggregate(indicators, HealthLevelOfDetail.STATUS_DESCRIPTION_DETAILS)

        then:
        calls.get() == 0

        when:
        List<HealthResult> results = Flux.from(publisher).collectList().block()

        then:
        calls.get() == 1
        results.size() == 1
        results[0].name == 'foo'
        results[0].status == HealthStatus.UP
        results[0].details.a.status == HealthStatus.UP
    }

    void 'aggregateAsync with a name combines a list of results'() {
        when:
        HealthResult result = aggregator.aggregateAsync('jdbc', [
                HealthResult.builder('one', HealthStatus.UP).build(),
                HealthResult.builder('two', HealthStatus.DOWN).build()
        ]).toCompletableFuture().getNow(null)

        then:
        result.name == 'jdbc'
        result.status == HealthStatus.DOWN
        (result.details as Map).keySet() == ['one', 'two'] as Set
    }

    void 'aggregate with a name collects the publisher'() {
        when:
        HealthResult result = Mono.from(aggregator.aggregate('jdbc', Flux.just(
                HealthResult.builder('one', HealthStatus.UP).build(),
                HealthResult.builder('two', HealthStatus.UP).build()
        ))).block()
        HealthResult empty = Mono.from(aggregator.aggregate('none', Flux.empty())).block()

        then:
        result.name == 'jdbc'
        result.status == HealthStatus.UP
        (result.details as Map).keySet() == ['one', 'two'] as Set
        empty.name == 'none'
        empty.status == HealthStatus.UNKNOWN
    }

    void 'the default aggregateAsync methods adapt a publisher-only aggregator'() {
        given:
        HealthAggregator<HealthResult> publisherOnly = new HealthAggregator<HealthResult>() {
            @Override
            Publisher<HealthResult> aggregate(HealthIndicator[] indicators, HealthLevelOfDetail healthLevelOfDetail) {
                return Mono.just(HealthResult.builder('custom', HealthStatus.UP).details([count: indicators.length]).build())
            }

            @Override
            Publisher<HealthResult> aggregate(String name, Publisher<HealthResult> results) {
                return Flux.from(results).collectList().map { list ->
                    HealthResult.builder(name, HealthStatus.UP).details([names: list*.name]).build()
                }
            }
        }

        when:
        HealthResult all = publisherOnly.aggregateAsync(indicators([HealthStatus.UP, HealthStatus.UP]), HealthLevelOfDetail.STATUS)
                .toCompletableFuture().get(5, TimeUnit.SECONDS)
        HealthResult named = publisherOnly.aggregateAsync('jdbc', [HealthResult.builder('one', HealthStatus.UP).build()])
                .toCompletableFuture().get(5, TimeUnit.SECONDS)

        then:
        all.name == 'custom'
        all.details == [count: 2]
        named.name == 'jdbc'
        named.details == [names: ['one']]
    }

    void 'aggregateAsync keeps all the results of an indicator that emits several'() {
        given:
        HealthIndicator[] indicators = [
                new PublisherOnlyIndicator(Flux.just(
                        HealthResult.builder('one', HealthStatus.UP).build(),
                        HealthResult.builder('two', HealthStatus.DOWN).build()
                )),
                new AsyncIndicator(CompletableFuture.completedFuture(HealthResult.builder('three', HealthStatus.UP).build()))
        ]

        when:
        HealthResult result = aggregator.aggregateAsync(indicators, HealthLevelOfDetail.STATUS_DESCRIPTION_DETAILS)
                .toCompletableFuture().get(5, TimeUnit.SECONDS)
        HealthResult published = Mono.from(new DefaultHealthAggregator(context.getBean(ApplicationConfiguration))
                .aggregate(indicators.take(1) as HealthIndicator[], HealthLevelOfDetail.STATUS_DESCRIPTION_DETAILS)).block()

        then:
        (result.details as Map).keySet() == ['one', 'two', 'three'] as Set
        result.status == HealthStatus.DOWN
        (published.details as Map).keySet() == ['one', 'two'] as Set
    }

    void 'a subclass that overrides aggregate is called through it by the default aggregateAsync'() {
        given:
        def calls = new AtomicInteger()
        DefaultHealthAggregator custom = new DefaultHealthAggregator(context.getBean(ApplicationConfiguration)) {
            @Override
            Publisher<HealthResult> aggregate(HealthIndicator[] indicators, HealthLevelOfDetail healthLevelOfDetail) {
                calls.incrementAndGet()
                return Mono.just(HealthResult.builder('custom', HealthStatus.UP).build())
            }

            @Override
            Publisher<HealthResult> aggregate(String name, Publisher<HealthResult> results) {
                calls.incrementAndGet()
                return Mono.just(HealthResult.builder('custom-' + name, HealthStatus.UP).build())
            }
        }

        expect:
        custom.aggregateAsync(indicators([HealthStatus.DOWN]), HealthLevelOfDetail.STATUS).toCompletableFuture().get(5, TimeUnit.SECONDS).name == 'custom'
        custom.aggregateAsync('jdbc', []).toCompletableFuture().get(5, TimeUnit.SECONDS).name == 'custom-jdbc'
        calls.get() == 2
    }

    void 'a subclass that overrides aggregateResults is called through it by aggregate and aggregateAsync'() {
        given:
        def calls = new AtomicInteger()
        DefaultHealthAggregator custom = new DefaultHealthAggregator(context.getBean(ApplicationConfiguration)) {
            @Override
            protected Flux<HealthResult> aggregateResults(HealthIndicator[] indicators) {
                calls.incrementAndGet()
                return Flux.just(HealthResult.builder('replaced', HealthStatus.UP).build())
            }
        }

        when:
        HealthResult async = custom.aggregateAsync(indicators([HealthStatus.DOWN]), HealthLevelOfDetail.STATUS_DESCRIPTION_DETAILS)
                .toCompletableFuture().get(5, TimeUnit.SECONDS)
        HealthResult published = Mono.from(custom.aggregate(indicators([HealthStatus.DOWN]), HealthLevelOfDetail.STATUS_DESCRIPTION_DETAILS)).block()

        then:
        (async.details as Map).keySet() == ['replaced'] as Set
        async.status == HealthStatus.UP
        (published.details as Map).keySet() == ['replaced'] as Set
        calls.get() == 2
    }

    void 'cancelling the publisher of the aggregation cancels the indicators'() {
        given:
        def cancelled = new AtomicInteger()
        def pending = new CompletableFuture<HealthResult>()
        HealthIndicator[] publisherOnly = [new PublisherOnlyIndicator(Mono.<HealthResult> never().doOnCancel { cancelled.incrementAndGet() })]
        HealthIndicator[] async = [new AsyncIndicator(pending)]

        when:
        Mono.from(new DefaultHealthAggregator(context.getBean(ApplicationConfiguration)).aggregate(publisherOnly, HealthLevelOfDetail.STATUS)).subscribe().dispose()
        Mono.from(aggregator.aggregate(publisherOnly, HealthLevelOfDetail.STATUS)).subscribe().dispose()
        Mono.from(aggregator.aggregate(async, HealthLevelOfDetail.STATUS)).subscribe().dispose()

        then:
        cancelled.get() == 2
        pending.cancelled
    }

    void 'the default bean is the aggregator that combines the stages'() {
        expect:
        ApplicationContext.run(['endpoints.health.enabled': true]).withCloseable {
            it.getBean(HealthAggregator) instanceof AsyncHealthAggregator
        }
    }

    private static HealthIndicator[] indicators(List<HealthStatus> statuses) {
        int i = 0
        return statuses.collect { status ->
            new AsyncIndicator(CompletableFuture.completedFuture(HealthResult.builder("indicator${i++}".toString(), status).build()))
        } as HealthIndicator[]
    }

    static class AsyncIndicator implements HealthIndicator {
        final CompletableFuture<HealthResult> future

        AsyncIndicator(CompletableFuture<HealthResult> future) {
            this.future = future
        }

        @Override
        Publisher<HealthResult> getResult() {
            throw new UnsupportedOperationException('the aggregator calls getResultAsync')
        }

        @Override
        CompletionStage<List<HealthResult>> getResultAsync() {
            return CompletionStagePublishers.cancelling(future, future.thenApply { [it] })
        }
    }

    static class PublisherOnlyIndicator implements HealthIndicator {
        final Publisher<HealthResult> publisher

        PublisherOnlyIndicator(Publisher<HealthResult> publisher) {
            this.publisher = publisher
        }

        @Override
        Publisher<HealthResult> getResult() {
            return publisher
        }
    }

    static class ThrowingIndicator implements HealthIndicator {
        final RuntimeException error

        ThrowingIndicator(RuntimeException error) {
            this.error = error
        }

        @Override
        Publisher<HealthResult> getResult() {
            throw error
        }

        @Override
        CompletionStage<List<HealthResult>> getResultAsync() {
            throw error
        }
    }
}

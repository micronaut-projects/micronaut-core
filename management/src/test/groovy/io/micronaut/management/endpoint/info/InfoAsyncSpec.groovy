package io.micronaut.management.endpoint.info

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.context.env.PropertySource
import io.micronaut.core.async.publisher.CompletionStagePublishers
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.core.io.ResourceResolver
import io.micronaut.management.endpoint.info.impl.ReactiveInfoAggregator
import io.micronaut.management.endpoint.info.source.BuildInfoSource
import io.micronaut.management.endpoint.info.source.ConfigurationInfoSource
import io.micronaut.management.endpoint.info.source.GitInfoSource
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class InfoAsyncSpec extends Specification {

    ReactiveInfoAggregator aggregator = new ReactiveInfoAggregator()

    void 'the default getSourceAsync adapts the first property source of the publisher'() {
        given:
        def cancelled = new AtomicBoolean()
        InfoSource source = new PublisherOnlySource(Flux.just(
                new MapPropertySource('first', [a: 1]),
                new MapPropertySource('second', [a: 2])
        ).doOnCancel { cancelled.set(true) })

        expect:
        source.sourceAsync.toCompletableFuture().get(5, TimeUnit.SECONDS).name == 'first'
        cancelled.get()
        new PublisherOnlySource(Publishers.empty()).sourceAsync.toCompletableFuture().get(5, TimeUnit.SECONDS) == null
    }

    void 'aggregateAsync gives priority to the earlier sources, and treats a missing source as empty'() {
        given:
        def late = new CompletableFuture<PropertySource>()
        InfoSource[] sources = [
                new AsyncSource(late),
                new PublisherOnlySource(Flux.just(new MapPropertySource('second', [a: 'second', b: 'second', c: [d: 'nested']]))),
                new PublisherOnlySource(Publishers.empty()),
                new AsyncSource(CompletableFuture.completedFuture(null))
        ]

        when:
        def stage = aggregator.aggregateAsync(sources).toCompletableFuture()

        then:
        !stage.done

        when:
        late.complete(new MapPropertySource('first', [a: 'first']))

        then:
        stage.get(5, TimeUnit.SECONDS) == [a: 'first', b: 'second', c: [d: 'nested']]
    }

    void 'aggregate emits the same result as aggregateAsync'() {
        given:
        InfoSource[] sources = [new PublisherOnlySource(Mono.just(new MapPropertySource('one', [a: 1]) as PropertySource))]

        expect:
        Mono.from(aggregator.aggregate(sources)).block() == [a: 1]
        aggregator.aggregateAsync(sources).toCompletableFuture().get(5, TimeUnit.SECONDS) == [a: 1]
    }

    void 'a mock source that only stubs getSource is called through it'() {
        given:
        InfoSource mock = Mock(InfoSource)
        mock.getSource() >> Mono.just(new MapPropertySource('mocked', [m: 1]) as PropertySource)
        InfoSource[] sources = [mock]

        expect:
        aggregator.aggregateAsync(sources).toCompletableFuture().get(5, TimeUnit.SECONDS) == [m: 1]
    }

    void 'the first failing source fails the aggregation and cancels the stages of the framework'() {
        given:
        def error = new IllegalStateException('boom')
        CompletableFuture<PropertySource> pending = CompletionStagePublishers.future()
        def shared = new CompletableFuture<PropertySource>()
        InfoSource[] sources = [new AsyncSource(pending), new AsyncSource(shared), new PublisherOnlySource(Mono.error(error))]

        when:
        aggregator.aggregateAsync(sources).toCompletableFuture().get(5, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause.is(error)
        pending.cancelled
        !shared.done

        when:
        InfoSource[] publisherSources = [new PublisherOnlySource(Mono.never()), new PublisherOnlySource(Mono.error(error))]
        Mono.from(aggregator.aggregate(publisherSources)).block()

        then:
        def e2 = thrown(IllegalStateException)
        e2.is(error)
    }

    void 'the default aggregateAsync adapts a publisher-only aggregator'() {
        given:
        InfoAggregator<Map<String, Object>> publisherOnly = { InfoSource[] sources -> Mono.just([count: sources.length] as Map<String, Object>) } as InfoAggregator<Map<String, Object>>

        expect:
        publisherOnly.aggregateAsync([] as InfoSource[]).toCompletableFuture().get(5, TimeUnit.SECONDS) == [count: 0]
    }

    void 'the built-in sources complete their stage with the publisher property source'() {
        given:
        ApplicationContext context = ApplicationContext.run(['info.test': 'foo'])
        def resolver = context.getBean(ResourceResolver)
        def git = new GitInfoSource(resolver, 'git.properties')
        def missingGit = new GitInfoSource(resolver, 'missing-git.properties')
        def build = new BuildInfoSource(resolver, 'META-INF/build-info.properties')
        def missingBuild = new BuildInfoSource(resolver, 'META-INF/missing-build-info.properties')
        def configuration = context.getBean(ConfigurationInfoSource)

        expect:
        git.sourceAsync.toCompletableFuture().getNow(null).name == Mono.from(git.source).block().name
        git.sourceAsync.toCompletableFuture().getNow(null).get('git.branch') == 'master'
        missingGit.sourceAsync.toCompletableFuture().getNow(null) == null
        Flux.from(missingGit.source).collectList().block() == []
        build.sourceAsync.toCompletableFuture().getNow(null) != null
        build.sourceAsync.toCompletableFuture().getNow(null).name == Mono.from(build.source).block().name
        missingBuild.sourceAsync.toCompletableFuture().getNow(null) == null
        Flux.from(missingBuild.source).collectList().block() == []
        configuration.sourceAsync.toCompletableFuture().getNow(null).get('test') == 'foo'
        Mono.from(configuration.source).block().get('test') == 'foo'

        cleanup:
        context.close()
    }

    void 'a subclass of the aggregator that overrides aggregate is called through it by the default aggregateAsync'() {
        given:
        def calls = new java.util.concurrent.atomic.AtomicInteger()
        ReactiveInfoAggregator custom = new ReactiveInfoAggregator() {
            @Override
            Publisher<Map<String, Object>> aggregate(InfoSource[] sources) {
                calls.incrementAndGet()
                return Mono.just([custom: true] as Map<String, Object>)
            }
        }

        expect:
        custom.aggregateAsync([] as InfoSource[]).toCompletableFuture().get(5, TimeUnit.SECONDS) == [custom: true]
        calls.get() == 1
    }

    void 'a subclass of the aggregator that overrides aggregateResults is called through it by aggregate and aggregateAsync'() {
        given:
        def calls = new java.util.concurrent.atomic.AtomicInteger()
        ReactiveInfoAggregator custom = new ReactiveInfoAggregator() {
            @Override
            protected Flux<Map.Entry<Integer, PropertySource>> aggregateResults(InfoSource[] sources) {
                calls.incrementAndGet()
                return Flux.just(Map.entry(0, new MapPropertySource('replaced', [replaced: true])) as Map.Entry<Integer, PropertySource>)
            }
        }
        InfoSource[] sources = [new AsyncSource(CompletableFuture.completedFuture(new MapPropertySource('one', [a: 1])))]

        expect:
        custom.aggregateAsync(sources).toCompletableFuture().get(5, TimeUnit.SECONDS) == [replaced: true]
        Mono.from(custom.aggregate(sources)).block() == [replaced: true]
        calls.get() == 2
    }

    void 'subclasses of the built-in sources that override getSource are called through it'() {
        given:
        ApplicationContext context = ApplicationContext.run()
        def resolver = context.getBean(ResourceResolver)
        def git = new GitInfoSource(resolver, 'git.properties') {
            @Override
            Publisher<PropertySource> getSource() {
                return Mono.just(new MapPropertySource('overridden', [o: 1]) as PropertySource)
            }
        }
        def build = new BuildInfoSource(resolver, 'META-INF/build-info.properties') {
            @Override
            Publisher<PropertySource> getSource() {
                return Mono.just(new MapPropertySource('overridden', [o: 2]) as PropertySource)
            }
        }

        expect:
        git.sourceAsync.toCompletableFuture().get(5, TimeUnit.SECONDS).name == 'overridden'
        build.sourceAsync.toCompletableFuture().get(5, TimeUnit.SECONDS).get('o') == 2

        cleanup:
        context.close()
    }

    void 'cancelling the aggregation cancels the sources'() {
        given:
        def cancelled = new java.util.concurrent.atomic.AtomicInteger()
        CompletableFuture<PropertySource> pending = CompletionStagePublishers.future()
        InfoSource[] publisherOnly = [new PublisherOnlySource(Mono.<PropertySource> never().doOnCancel { cancelled.incrementAndGet() })]
        InfoSource[] async = [new AsyncSource(pending)]

        when:
        Mono.from(aggregator.aggregate(publisherOnly)).subscribe().dispose()
        aggregator.aggregateAsync(publisherOnly).toCompletableFuture().cancel(false)
        aggregator.aggregateAsync(async).toCompletableFuture().cancel(false)

        then:
        cancelled.get() == 2
        pending.cancelled
    }

    void 'the default bean is the aggregator that combines the stages'() {
        expect:
        ApplicationContext.run(['endpoints.info.enabled': true]).withCloseable {
            it.getBean(InfoAggregator).getClass() == ReactiveInfoAggregator
        }
    }

    static class PublisherOnlySource implements InfoSource {
        final Publisher<PropertySource> publisher

        PublisherOnlySource(Publisher<PropertySource> publisher) {
            this.publisher = publisher
        }

        @Override
        Publisher<PropertySource> getSource() {
            return publisher
        }
    }

    static class AsyncSource implements InfoSource {
        final CompletableFuture<PropertySource> future

        AsyncSource(CompletableFuture<PropertySource> future) {
            this.future = future
        }

        @Override
        Publisher<PropertySource> getSource() {
            throw new UnsupportedOperationException('the aggregator calls getSourceAsync')
        }

        @Override
        CompletionStage<PropertySource> getSourceAsync() {
            return future
        }
    }
}

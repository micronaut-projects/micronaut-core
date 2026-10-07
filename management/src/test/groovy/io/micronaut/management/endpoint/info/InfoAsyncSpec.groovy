package io.micronaut.management.endpoint.info

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.context.env.PropertySource
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
        InfoSource[] sources = [new AsyncSource(CompletableFuture.completedFuture(new MapPropertySource('one', [a: 1])))]

        expect:
        Mono.from(aggregator.aggregate(sources)).block() == [a: 1]
        aggregator.aggregateAsync(sources).toCompletableFuture().get(5, TimeUnit.SECONDS) == [a: 1]
    }

    void 'aggregateResultsAsync keys each property source by the index of its source'() {
        given:
        InfoSource[] sources = [
                new AsyncSource(CompletableFuture.completedFuture(new MapPropertySource('one', [a: 1]))),
                new PublisherOnlySource(Publishers.empty())
        ]

        when:
        List<Map.Entry<Integer, PropertySource>> results = aggregator.aggregateResultsAsync(sources).toCompletableFuture().get(5, TimeUnit.SECONDS)

        then:
        results*.key == [0, 1]
        results[0].value.name == 'one'
        !results[1].value.iterator().hasNext()
    }

    void 'the first failing source fails the aggregation and cancels the others'() {
        given:
        def error = new IllegalStateException('boom')
        def pending = new CompletableFuture<PropertySource>()
        InfoSource[] sources = [new AsyncSource(pending), new PublisherOnlySource(Mono.error(error))]

        when:
        aggregator.aggregateAsync(sources).toCompletableFuture().get(5, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause.is(error)
        pending.cancelled

        when:
        InfoSource[] publisherSources = [new AsyncSource(new CompletableFuture<PropertySource>()), new PublisherOnlySource(Mono.error(error))]
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

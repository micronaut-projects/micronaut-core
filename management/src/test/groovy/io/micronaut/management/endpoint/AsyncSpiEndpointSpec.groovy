package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.health.HealthStatus
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.logging.LogLevel
import io.micronaut.management.endpoint.health.HealthLevelOfDetail
import io.micronaut.management.endpoint.info.InfoAggregator
import io.micronaut.management.endpoint.info.InfoSource
import io.micronaut.management.endpoint.info.impl.ReactiveInfoAggregator
import io.micronaut.management.endpoint.loggers.LoggersManager
import io.micronaut.management.endpoint.loggers.ManagedLoggingSystem
import io.micronaut.management.endpoint.loggers.impl.DefaultLoggersManager
import io.micronaut.management.health.aggregator.DefaultHealthAggregator
import io.micronaut.management.health.aggregator.HealthAggregator
import io.micronaut.management.health.indicator.AbstractHealthIndicator
import io.micronaut.management.health.indicator.HealthIndicator
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.runtime.ApplicationConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * The endpoints keep their publisher return types, and are implemented on the
 * {@link CompletionStage} counterparts of the management SPIs, which adapt the implementations
 * that only provide the publisher methods.
 */
class AsyncSpiEndpointSpec extends Specification {

    void 'the health endpoint reports the native async indicators with the publisher-only ones'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                       : 'AsyncSpiEndpointSpec',
                'micronaut.application.name'      : 'foo',
                'endpoints.health.sensitive'      : false,
                'endpoints.health.details-visible': 'ANONYMOUS',
                'endpoints.health.disk-space.enabled': false,
                'async.indicator.status'          : status
        ])
        BlockingHttpClient client = server.applicationContext.createBean(HttpClient, server.URL).toBlocking()

        when:
        def response = exchange(client, '/health')
        Map body = response.body()

        then:
        response.code() == expectedCode
        body.name == 'foo'
        body.status == status
        body.details.asyncOnly.status == status
        body.details.asyncOnly.details == [native: true]
        body.details.publisherOnly.status == 'UP'
        body.details.publisherOnly.details == [publisher: true]

        cleanup:
        client.close()
        server.close()

        where:
        status | expectedCode
        'UP'   | HttpStatus.OK.code
        'DOWN' | HttpStatus.SERVICE_UNAVAILABLE.code
    }

    void 'the endpoints adapt publisher-only aggregators and loggers managers'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                    : 'AsyncSpiEndpointSpec.publisherOnly',
                'endpoints.health.sensitive'   : false,
                'endpoints.info.sensitive'     : false,
                'endpoints.loggers.enabled'    : true,
                'endpoints.loggers.sensitive'  : false
        ])
        BlockingHttpClient client = server.applicationContext.createBean(HttpClient, server.URL).toBlocking()

        expect:
        client.retrieve('/health', Map) == [name: 'publisherOnlyAggregator', status: 'UP']
        client.retrieve('/info', Map) == [aggregator: 'publisherOnly']
        client.retrieve('/loggers', Map) == [loggers: 'publisherOnly']
        client.retrieve('/loggers/foo', Map) == [logger: 'foo']

        cleanup:
        client.close()
        server.close()
    }

    void 'the health endpoint reports every result of an indicator, and an AbstractHealthIndicator that overrides getResult'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                          : 'AsyncSpiEndpointSpec.overrides',
                'endpoints.health.sensitive'         : false,
                'endpoints.health.details-visible'   : 'ANONYMOUS',
                'endpoints.health.disk-space.enabled': false
        ])
        BlockingHttpClient client = server.applicationContext.createBean(HttpClient, server.URL).toBlocking()

        when:
        Map body = client.retrieve('/health', Map)

        then:
        body.details.first.status == 'UP'
        body.details.second.status == 'UP'
        body.details.overridden.status == 'UP'
        body.details.overridden.details == [overridden: true]

        cleanup:
        client.close()
        server.close()
    }

    void 'the health endpoint calls a replaced aggregator subclass through #method'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                          : 'AsyncSpiEndpointSpec.aggregator',
                'aggregator.override'                : method,
                'endpoints.health.sensitive'         : false,
                'endpoints.health.details-visible'   : 'ANONYMOUS',
                'endpoints.health.disk-space.enabled': false
        ])
        BlockingHttpClient client = server.applicationContext.createBean(HttpClient, server.URL).toBlocking()

        when:
        Map body = client.retrieve('/health', Map)

        then:
        body.name == expectedName
        body.details.keySet() == expectedDetails as Set

        cleanup:
        client.close()
        server.close()

        where:
        method             | expectedName          | expectedDetails
        'aggregate'        | 'subclassAggregate'   | ['custom']
        'aggregateResults' | 'application'         | ['replacedResult']
    }

    private static exchange(BlockingHttpClient client, String uri) {
        try {
            return client.exchange(uri, Map)
        } catch (HttpClientResponseException e) {
            return e.response
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'AsyncSpiEndpointSpec')
    static class AsyncOnlyIndicator implements HealthIndicator {
        final HealthStatus status

        AsyncOnlyIndicator(@io.micronaut.context.annotation.Value('${async.indicator.status}') String status) {
            this.status = status == 'UP' ? HealthStatus.UP : HealthStatus.DOWN
        }

        @Override
        Publisher<HealthResult> getResult() {
            throw new UnsupportedOperationException('the aggregator calls getResultAsync')
        }

        @Override
        CompletionStage<List<HealthResult>> getResultAsync() {
            return CompletableFuture.supplyAsync {
                [HealthResult.builder('asyncOnly', status).details([native: true]).build()]
            }
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'AsyncSpiEndpointSpec')
    static class PublisherOnlyIndicator implements HealthIndicator {
        @Override
        Publisher<HealthResult> getResult() {
            return Mono.just(HealthResult.builder('publisherOnly', HealthStatus.UP).details([publisher: true]).build())
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'AsyncSpiEndpointSpec.overrides')
    static class MultipleResultsIndicator implements HealthIndicator {
        @Override
        Publisher<HealthResult> getResult() {
            return Flux.just(
                    HealthResult.builder('first', HealthStatus.UP).build(),
                    HealthResult.builder('second', HealthStatus.UP).build()
            )
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'AsyncSpiEndpointSpec.overrides')
    static class OverridingAbstractIndicator extends AbstractHealthIndicator<Map<String, Object>> {
        @Override
        Publisher<HealthResult> getResult() {
            return Mono.just(HealthResult.builder('overridden', HealthStatus.UP).details([overridden: true]).build())
        }

        @Override
        protected Map<String, Object> getHealthInformation() {
            throw new UnsupportedOperationException('getResult is overridden')
        }

        @Override
        protected String getName() {
            return 'overridden'
        }
    }

    @Singleton
    @Replaces(DefaultHealthAggregator)
    @Requires(property = 'spec.name', value = 'AsyncSpiEndpointSpec.aggregator')
    static class SubclassHealthAggregator extends DefaultHealthAggregator {
        final String override

        SubclassHealthAggregator(ApplicationConfiguration applicationConfiguration,
                                 @io.micronaut.context.annotation.Value('${aggregator.override}') String override) {
            super(applicationConfiguration)
            this.override = override
        }

        @Override
        Publisher<HealthResult> aggregate(HealthIndicator[] indicators, HealthLevelOfDetail healthLevelOfDetail) {
            if (override == 'aggregate') {
                return Mono.just(HealthResult.builder('subclassAggregate', HealthStatus.UP).details([custom: [status: 'UP']]).build())
            }
            return super.aggregate(indicators, healthLevelOfDetail)
        }

        @Override
        protected Flux<HealthResult> aggregateResults(HealthIndicator[] indicators) {
            if (override == 'aggregateResults') {
                return Flux.just(HealthResult.builder('replacedResult', HealthStatus.UP).build())
            }
            return super.aggregateResults(indicators)
        }
    }

    @Singleton
    @Replaces(DefaultHealthAggregator)
    @Requires(property = 'spec.name', value = 'AsyncSpiEndpointSpec.publisherOnly')
    static class PublisherOnlyHealthAggregator implements HealthAggregator<HealthResult> {
        @Override
        Publisher<HealthResult> aggregate(HealthIndicator[] indicators, HealthLevelOfDetail healthLevelOfDetail) {
            return Flux.just(HealthResult.builder('publisherOnlyAggregator', HealthStatus.UP).build())
        }

        @Override
        Publisher<HealthResult> aggregate(String name, Publisher<HealthResult> results) {
            return Flux.just(HealthResult.builder(name, HealthStatus.UP).build())
        }
    }

    @Singleton
    @Replaces(ReactiveInfoAggregator)
    @Requires(property = 'spec.name', value = 'AsyncSpiEndpointSpec.publisherOnly')
    static class PublisherOnlyInfoAggregator implements InfoAggregator<Map<String, Object>> {
        @Override
        Publisher<Map<String, Object>> aggregate(InfoSource[] sources) {
            return Flux.just([aggregator: 'publisherOnly'] as Map<String, Object>)
        }
    }

    @Singleton
    @Replaces(DefaultLoggersManager)
    @Requires(property = 'spec.name', value = 'AsyncSpiEndpointSpec.publisherOnly')
    static class PublisherOnlyLoggersManager implements LoggersManager<Map<String, Object>> {
        @Override
        Publisher<Map<String, Object>> getLoggers(ManagedLoggingSystem loggingSystem) {
            return Flux.just([loggers: 'publisherOnly'] as Map<String, Object>)
        }

        @Override
        Publisher<Map<String, Object>> getLogger(ManagedLoggingSystem loggingSystem, String name) {
            return Flux.just([logger: name] as Map<String, Object>)
        }

        @Override
        void setLogLevel(ManagedLoggingSystem loggingSystem, String name, LogLevel level) {
        }
    }
}

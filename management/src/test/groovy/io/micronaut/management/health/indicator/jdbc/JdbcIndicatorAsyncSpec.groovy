package io.micronaut.management.health.indicator.jdbc

import io.micronaut.context.ApplicationContext
import io.micronaut.health.HealthStatus
import io.micronaut.management.endpoint.health.HealthLevelOfDetail
import io.micronaut.management.health.aggregator.HealthAggregator
import io.micronaut.management.health.indicator.HealthIndicator
import io.micronaut.management.health.indicator.HealthResult
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.SQLException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class JdbcIndicatorAsyncSpec extends Specification {

    @AutoCleanup('shutdownNow')
    ExecutorService executor = Executors.newSingleThreadExecutor()

    void 'the jdbc indicator checks the data sources without a publisher'() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'datasources.one.url': 'jdbc:h2:mem:asyncOneDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE'
        ])
        JdbcIndicator indicator = context.getBean(JdbcIndicator)

        when:
        HealthResult async = indicator.resultAsync.toCompletableFuture().get(10, TimeUnit.SECONDS).first()
        HealthResult published = Mono.from(indicator.result).block()

        then:
        indicator instanceof AsyncJdbcIndicator
        async.name == 'jdbc'
        async.status == HealthStatus.UP
        async.details.'jdbc:h2:mem:asyncOneDb'.status == HealthStatus.UP
        async.details.'jdbc:h2:mem:asyncOneDb'.details.database == 'H2'
        published.name == async.name
        published.status == async.status
        published.details.keySet() == async.details.keySet()

        cleanup:
        context.close()
    }

    void 'a data source that fails to connect is reported as DOWN'() {
        given:
        ApplicationContext context = ApplicationContext.run()
        DataSource failing = [getConnection: { -> throw new SQLException('no connection') }] as DataSource
        def indicator = new AsyncJdbcIndicator(executor, [failing] as DataSource[], null, context.getBean(HealthAggregator))

        when:
        HealthResult result = indicator.resultAsync.toCompletableFuture().get(10, TimeUnit.SECONDS).first()

        then:
        result.name == 'jdbc'
        result.status == HealthStatus.DOWN
        (result.details as Map).size() == 1
        (result.details as Map).values().first().status == HealthStatus.DOWN

        cleanup:
        context.close()
    }

    void 'without data sources the indicator has no result'() {
        given:
        def indicator = new AsyncJdbcIndicator(executor, new DataSource[0], null, Mock(HealthAggregator))

        expect:
        indicator.resultAsync.toCompletableFuture().getNow(null) == []
        Flux.from(indicator.result).collectList().block() == []
    }

    void 'a publisher-only aggregator is adapted by the default aggregateAsync'() {
        given:
        DataSource failing = [getConnection: { -> throw new SQLException('no connection') }] as DataSource
        HealthAggregator<HealthResult> publisherOnly = new HealthAggregator<HealthResult>() {
            @Override
            Publisher<HealthResult> aggregate(HealthIndicator[] indicators, HealthLevelOfDetail healthLevelOfDetail) {
                return Flux.empty()
            }

            @Override
            Publisher<HealthResult> aggregate(String name, Publisher<HealthResult> results) {
                return Flux.from(results).collectList().map { list ->
                    HealthResult.builder(name, HealthStatus.UNKNOWN).details([count: list.size()]).build()
                }
            }
        }
        def indicator = new AsyncJdbcIndicator(executor, [failing, failing] as DataSource[], null, publisherOnly)

        when:
        HealthResult result = indicator.resultAsync.toCompletableFuture().get(10, TimeUnit.SECONDS).first()

        then:
        result.name == 'jdbc'
        result.status == HealthStatus.UNKNOWN
        result.details == [count: 2]
    }

    void 'a subclass of the indicator that overrides getResult is called through it'() {
        given:
        def indicator = new JdbcIndicator(executor, new DataSource[0], null, Mock(HealthAggregator)) {
            @Override
            Publisher<HealthResult> getResult() {
                return Mono.just(HealthResult.builder('overridden', HealthStatus.UP).build())
            }
        }

        expect:
        indicator.resultAsync.toCompletableFuture().get(5, TimeUnit.SECONDS)*.name == ['overridden']
    }
}

package io.micronaut.management.endpoint.loggers

import io.micronaut.logging.LogLevel
import io.micronaut.management.endpoint.loggers.impl.DefaultLoggersManager
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class LoggersManagerAsyncSpec extends Specification {

    ManagedLoggingSystem loggingSystem = new TestLoggingSystem()

    void 'the default loggers manager completes the async methods with the publisher values'() {
        given:
        def manager = new DefaultLoggersManager()

        when:
        Map<String, Object> loggers = manager.getLoggersAsync(loggingSystem).toCompletableFuture().getNow(null)
        Map<String, Object> logger = manager.getLoggerAsync(loggingSystem, 'foo').toCompletableFuture().getNow(null)

        then:
        loggers == Mono.from(manager.getLoggers(loggingSystem)).block()
        loggers.levels == LogLevel.values().toList()
        loggers.loggers == [ROOT: [configuredLevel: LogLevel.INFO, effectiveLevel: LogLevel.INFO],
                            foo : [configuredLevel: LogLevel.NOT_SPECIFIED, effectiveLevel: LogLevel.INFO]]
        logger == Mono.from(manager.getLogger(loggingSystem, 'foo')).block()
        logger == [configuredLevel: LogLevel.NOT_SPECIFIED, effectiveLevel: LogLevel.INFO]
    }

    void 'the default async methods adapt a publisher-only loggers manager'() {
        given:
        def cancelled = new AtomicBoolean()
        LoggersManager<String> manager = new LoggersManager<String>() {
            @Override
            Publisher<String> getLoggers(ManagedLoggingSystem system) {
                return Flux.just('all', 'ignored').doOnCancel { cancelled.set(true) }
            }

            @Override
            Publisher<String> getLogger(ManagedLoggingSystem system, String name) {
                return Mono.just(name)
            }

            @Override
            void setLogLevel(ManagedLoggingSystem system, String name, LogLevel level) {
            }
        }

        expect:
        manager.getLoggersAsync(loggingSystem).toCompletableFuture().get(5, TimeUnit.SECONDS) == 'all'
        cancelled.get()
        manager.getLoggerAsync(loggingSystem, 'foo').toCompletableFuture().get(5, TimeUnit.SECONDS) == 'foo'
    }

    static class TestLoggingSystem implements ManagedLoggingSystem {
        @Override
        Collection<LoggerConfiguration> getLoggers() {
            return [new LoggerConfiguration('ROOT', LogLevel.INFO, LogLevel.INFO), getLogger('foo')]
        }

        @Override
        LoggerConfiguration getLogger(String name) {
            return new LoggerConfiguration(name, LogLevel.NOT_SPECIFIED, LogLevel.INFO)
        }

        @Override
        void setLogLevel(String name, LogLevel level) {
        }
    }
}

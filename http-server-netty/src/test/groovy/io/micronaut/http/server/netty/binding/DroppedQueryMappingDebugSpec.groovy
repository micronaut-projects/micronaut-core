package io.micronaut.http.server.netty.binding

import ch.qos.logback.classic.Logger
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.binding.RequestArgumentSatisfier
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.server.netty.configuration.MemoryAppender
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import spock.util.concurrent.PollingConditions

class DroppedQueryMappingDebugSpec extends AbstractMicronautSpec {

    def conditions = new PollingConditions(timeout: 10, initialDelay: 0.1, factor: 1.25)
    MemoryAppender appender

    @Override
    Map<String, Object> getConfiguration() {
        ['logger.levels.io.micronaut.http.server.binding': 'DEBUG']
    }

    def setup() {
        appender = new MemoryAppender()
        ((Logger) LoggerFactory.getLogger(RequestArgumentSatisfier)).addAppender(appender)
        appender.start()
    }

    def cleanup() {
        appender.stop()
        ((Logger) LoggerFactory.getLogger(RequestArgumentSatisfier)).detachAppender(appender)
    }

    def "test we get logging for failed #description #type conversion at #path"() {
        when:
        def response = get(path)

        then:
        response.body() == expected
        conditions.eventually {
            println appender.events
            assert appender.events == ["[DEBUG] Failed to convert argument 'query' to $type at uri /dropped$path"]
        }

        where:
        path                          | description                 | type                | expected
        '/int?query=non-int'          | "nullable"                  | "Integer"           | "int null"
        '/int-default?query=non-int'  | "non-nullable with default" | "Integer"           | "int-default 42"
        '/int-optional?query=non-int' | "non-nullable optional"     | "Optional<Integer>" | "int-optional Optional.empty"
        '/enum?query=baking'          | "nullable"                  | "State"             | "enum null"
        '/enum-default?query=baking'  | "non-nullable with default" | "State"             | "enum-default WARM"
        '/enum-optional?query=baking' | "non-nullable optional"     | "Optional<State>"   | "enum-optional Optional.empty"
    }

    private HttpResponse<?> get(uri) {
        Flux.from(rxClient.exchange(HttpRequest.create(HttpMethod.GET, "/dropped$uri"), String)).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()
    }

    @Controller(value = "/dropped", produces = MediaType.TEXT_PLAIN)
    static class FormattedController {

        static enum State {
            COOL,
            WARM,
            HOT
        }

        @Get("/int")
        String asInt(@QueryValue @Nullable Integer query) {
            "int $query"
        }

        @Get("/int-default")
        String asIntDefault(@QueryValue(defaultValue = "42") Integer query) {
            "int-default $query"
        }

        @Get("/int-optional")
        String asInt(@QueryValue Optional<Integer> query) {
            "int-optional $query"
        }

        @Get("/enum")
        String asEnum(@QueryValue @Nullable State query) {
            "enum $query"
        }

        @Get("/enum-default")
        String asEnumDefault(@QueryValue(defaultValue = "warm") State query) {
            "enum-default $query"
        }

        @Get("/enum-optional")
        String asEnum(@QueryValue Optional<State> query) {
            "enum-optional $query"
        }
    }
}
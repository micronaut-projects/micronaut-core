package io.micronaut.http

import ch.qos.logback.classic.Logger
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

@MicronautTest
@Property(name = 'micronaut.server.netty.access-logger.enabled', value = 'true')
@Property(name = 'micronaut.server.netty.access-logger.exclusions[0]', value = '/some/path')
@Property(name = 'micronaut.server.netty.access-logger.exclusions[1]', value = '/prefix.+')
@Property(name = 'spec.name', value = 'ExcludedHttpAccessLoggerSpec')
class ExcludedHttpAccessLoggerSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    PollingConditions conditions = new PollingConditions(timeout: 5, delay: 0.5)

    static MemoryAppender appender = new MemoryAppender()

    static {
        Logger l = (Logger) LoggerFactory.getLogger("HTTP_ACCESS_LOGGER")
        l.addAppender(appender)
        appender.start()
    }

    def setup() {
        appender.events.clear()
    }

    def "test paths are excluded for specified patterns"() {
        when:
        def paths = [
                [uri: '/prefix/a', logged: false],    // Matches /prefix.+
                [uri: '/prefix/b/c', logged: false],  // Matches /prefix.+
                [uri: '/prefix', logged: true],
                [uri: '/some/path', logged: false],   // Matches /some/path
                [uri: '/some/path/2', logged: true],
                [uri: '/another', logged: true],
        ]

        and:
        def responses = paths.collect {
            Flux.from(client.retrieve(HttpRequest.GET(it.uri), String)).blockFirst()
        }

        then:
        responses.size() == paths.size()
        responses.every { it == "ok" }

        conditions.eventually {
            def loggedUris = appender.events.collect {
                def matcher = (it =~ /^.+GET (?<uri>\S+).+$/)
                matcher.matches() ? matcher.group('uri') : it
            }

            loggedUris.toSet() == paths.findAll { it.logged }.uri.toSet()
        }
    }

    @Controller("/")
    @Requires(property = "spec.name", value = "ExcludedHttpAccessLoggerSpec")
    static class GetController {

        @Get('/{+path}')
        String simple() {
            return "ok"
        }

    }

}

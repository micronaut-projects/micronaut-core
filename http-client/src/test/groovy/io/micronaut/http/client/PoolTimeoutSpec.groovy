package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import reactor.core.publisher.Mono
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class PoolTimeoutSpec extends Specification {
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/9448')
    def "cancelled pool acquire should not leak connection"() {
        given:
        def ctx = ApplicationContext.run([
                "spec.name": "PoolTimeoutSpec",
                "micronaut.http.client.pool.enabled": true,
                "micronaut.http.client.pool.max-pending-connections": 1,
                "micronaut.http.client.pool.max-concurrent-http1-connections": 1,
                "micronaut.http.client.read-timeout": "1s",
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI)

        def fut1 = Mono.from(client.retrieve("/slow-controller?req=1")).toFuture().thenApply {
            TimeUnit.SECONDS.sleep(3)
            it
        }

        when:
        client.toBlocking().retrieve("/slow-controller?req=2")
        then:
        thrown(ReadTimeoutException)

        when:
        def r1 = fut1.get()
        then:
        r1 == "foo"

        when:
        def r2 = client.toBlocking().retrieve("/slow-controller?req=3")
        then:
        r2 == "foo"

        cleanup:
        client.close()
        server.stop()
        ctx.close()
    }

    @Controller("/slow-controller")
    static class SlowController {
        @Get
        @ExecuteOn(TaskExecutors.IO)
        String slow() {
            return "foo"
        }
    }
}

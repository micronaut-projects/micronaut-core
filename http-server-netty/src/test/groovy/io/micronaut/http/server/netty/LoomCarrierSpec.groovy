package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.netty.channel.loom.PrivateLoomSupport
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import spock.lang.Specification

class LoomCarrierSpec extends Specification {
    def test() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'LoomCarrierSpec',
                'micronaut.netty.event-loops.default.loom-carrier': true,
                'micronaut.netty.event-loops.default.num-threads': 1,
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        when:
        def s = client.retrieve("/loom-carrier")
        then:
        s == "loom-on-netty-1 on default-nioEventLoopGroup-3-1"

        cleanup:
        ctx.close()
    }

    @Controller("/loom-carrier")
    @Requires(property = "spec.name", value = "LoomCarrierSpec")
    static class MyCtrl {
        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get
        String foo() {
            return Thread.currentThread().getName() + " on " + PrivateLoomSupport.getCarrierThread().getName()
        }
    }
}

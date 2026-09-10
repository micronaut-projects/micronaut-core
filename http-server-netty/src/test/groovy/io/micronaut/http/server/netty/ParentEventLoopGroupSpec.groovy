package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.EventLoopGroup
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

class ParentEventLoopGroupSpec extends Specification {

    private static int executorCount(EventLoopGroup group) {
        int count = 0
        for (def executor : group) {
            count++
        }
        return count
    }

    private static String threadName(EventLoopGroup group) {
        group.next().submit({ Thread.currentThread().name } as Callable<String>).get(10, TimeUnit.SECONDS)
    }

    def 'acceptor event loop group uses a single, distinctly named thread by default'() {
        given:
        NettyHttpServer server = (NettyHttpServer) ApplicationContext.run(EmbeddedServer, ['spec.name': 'ParentEventLoopGroupSpec'])
        EventLoopGroup parentGroup = server.parentGroup

        expect:
        executorCount(parentGroup) == 1
        threadName(parentGroup).startsWith('parent-eventLoopGroup-')

        cleanup:
        server.stop()
    }

    def 'acceptor thread count configured on the event loop registry is honoured'() {
        given:
        NettyHttpServer server = (NettyHttpServer) ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                  : 'ParentEventLoopGroupSpec',
                'micronaut.netty.event-loops.parent.num-threads': 3
        ])

        expect:
        executorCount(server.parentGroup) == 3

        cleanup:
        server.stop()
    }

    def 'acceptor thread count configured on the server is honoured'() {
        given:
        NettyHttpServer server = (NettyHttpServer) ApplicationContext.run(EmbeddedServer, [
                'spec.name'                          : 'ParentEventLoopGroupSpec',
                'micronaut.server.netty.parent.threads': 3
        ])

        expect:
        executorCount(server.parentGroup) == 3
        threadName(server.parentGroup).startsWith('parent-eventLoopGroup-')

        cleanup:
        server.stop()
    }
}

package io.micronaut.http.netty.websocket

import io.netty.channel.DefaultEventLoop
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class ExecuteOrElseSpec extends Specification {

    def "the task runs on a live executor"() {
        given:
        def loop = new DefaultEventLoop()
        def ran = loop.newPromise()

        when:
        AbstractNettyWebSocketHandler.executeOrElse(loop, { ran.setSuccess(null) }, { ran.setFailure(new IllegalStateException("rejected")) })

        then:
        ran.await(5, TimeUnit.SECONDS)
        ran.isSuccess()

        cleanup:
        loop.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync()
    }

    def "the fallback runs in the caller when the executor is shut down"() {
        given:
        def loop = new DefaultEventLoop()
        loop.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync()
        def task = false
        def rejected = false

        when:
        AbstractNettyWebSocketHandler.executeOrElse(loop, { task = true }, { rejected = true })

        then:
        !task
        rejected
    }
}

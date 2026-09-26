package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.Channel
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets

/**
 * A failure while the per-request pipeline is being built (after the connection was acquired,
 * before the request is written) must fail the request, close the connection, and give the pool
 * handle back so that the pool can open a new connection for the next request.
 * <p>
 * The failure is provoked by removing the client's response handler, which is installed once
 * per connection, from the pooled connection: starting the next request on it then fails.
 */
class RequestPipelineFailureReleasesConnectionSpec extends Specification {

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            "spec.name": "RequestPipelineFailureReleasesConnectionSpec",
            "micronaut.http.client.pool.enabled": true,
            "micronaut.http.client.pool.max-concurrent-http1-connections": 1,
            "micronaut.http.client.pool.acquire-timeout": "3s",
            // longer than the acquire timeout, so that a leaked connection is not closed by the
            // read timeout and silently replaced before the following request gives up
            "micronaut.http.client.read-timeout": "30s",
    ])

    @AutoCleanup
    DefaultHttpClient client = server.applicationContext.createBean(DefaultHttpClient, server.URI)

    def "connection is closed and replaced when building the request pipeline fails (#description)"() {
        given: "a warmed up client with a single pooled connection"
        assert client.toBlocking().retrieve("/pipeline-failure/ok") == "ok"
        List<Channel> before = client.connectionManager().getChannels()
        assert before.size() == 1
        Channel first = before[0]
        assert first.isActive()

        and: "the response handler of that connection is gone, so that starting a request on it fails"
        first.eventLoop().submit {
            first.pipeline().remove(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE)
        }.get()

        when: "a request is sent on that connection"
        client.toBlocking().exchange(request, String)

        then: "the client reports the pipeline error"
        def e = thrown(Exception)
        causeChain(e).any { it instanceof IllegalStateException && it.message == "Not added to a channel" }

        and: "the connection the request was going to use is closed, and nothing is left running on it"
        new PollingConditions(timeout: 5).eventually {
            assert !first.isActive()
            assert client.connectionManager().liveRequestCount() == 0
        }

        when: "a normal request follows on the same client"
        def response = client.toBlocking().retrieve("/pipeline-failure/ok")

        then: "it succeeds on a new connection"
        response == "ok"
        List<Channel> after = client.connectionManager().getChannels()
        after.size() == 1
        after[0].isActive()
        after[0] != first

        where:
        description      | request
        "available body" | HttpRequest.POST("/pipeline-failure/echo", "hello".getBytes(StandardCharsets.UTF_8)).contentType(MediaType.TEXT_PLAIN_TYPE)
        "streaming body" | HttpRequest.POST("/pipeline-failure/echo", Flux.just("hel", "lo")).contentType(MediaType.TEXT_PLAIN_TYPE)
        "no body"        | HttpRequest.GET("/pipeline-failure/ok")
    }

    def "connection is closed and replaced when a customizer fails after the response handler took the request (#description)"() {
        given: "a client whose only customizer fails for the next request, after the response handler started it"
        // a customizer that is not wrapped in the composite customizer of the client registry,
        // which would log and swallow its failure
        def customizer = new FailingCustomizer()
        DefaultHttpClient customizedClient = DefaultHttpClient.builder()
                .uri(server.URI)
                .configuration(server.applicationContext.getBean(HttpClientConfiguration))
                .clientCustomizer(customizer)
                .build()

        and: "a warmed up single pooled connection"
        assert customizedClient.toBlocking().retrieve("/pipeline-failure/ok") == "ok"
        List<Channel> before = customizedClient.connectionManager().getChannels()
        assert before.size() == 1
        Channel first = before[0]
        customizer.failNext = true

        when: "a request is sent on that connection"
        customizedClient.toBlocking().exchange(request, String)

        then: "the client reports the customizer error"
        def e = thrown(Exception)
        causeChain(e).any { it.message == FailingCustomizer.MESSAGE }
        !customizer.failNext

        and: "the connection is closed, and nothing is left running on it"
        new PollingConditions(timeout: 5).eventually {
            assert !first.isActive()
            assert customizedClient.connectionManager().liveRequestCount() == 0
        }

        when: "a normal request follows on the same client"
        def response = customizedClient.toBlocking().retrieve(HttpRequest.POST("/pipeline-failure/echo", "again").contentType(MediaType.TEXT_PLAIN_TYPE), String)

        then: "it succeeds on a new connection"
        response == "again"
        List<Channel> after = customizedClient.connectionManager().getChannels()
        after.size() == 1
        after[0] != first

        cleanup:
        customizedClient.close()

        where:
        description                     | request
        "available body"                | HttpRequest.POST("/pipeline-failure/echo", "hello".getBytes(StandardCharsets.UTF_8)).contentType(MediaType.TEXT_PLAIN_TYPE)
        "available body, expect 100"    | HttpRequest.POST("/pipeline-failure/echo", "hello".getBytes(StandardCharsets.UTF_8)).contentType(MediaType.TEXT_PLAIN_TYPE).header(HttpHeaders.EXPECT, "100-continue")
        "streaming body"                | HttpRequest.POST("/pipeline-failure/echo", Flux.just("hel", "lo")).contentType(MediaType.TEXT_PLAIN_TYPE)
        "streaming body, expect 100"    | HttpRequest.POST("/pipeline-failure/echo", Flux.just("hel", "lo")).contentType(MediaType.TEXT_PLAIN_TYPE).header(HttpHeaders.EXPECT, "100-continue")
    }

    private static List<Throwable> causeChain(Throwable t) {
        List<Throwable> chain = []
        while (t != null && !chain.contains(t)) {
            chain.add(t)
            t = t.cause
        }
        chain
    }

    /**
     * Customizer that fails in {@link NettyClientCustomizer#onRequestPipelineBuilt()} once
     * {@link #failNext} is set, i.e. after the response handler of the connection already started
     * the request, but before anything was written.
     */
    static class FailingCustomizer implements NettyClientCustomizer {
        static final String MESSAGE = "customizer failure"

        volatile boolean failNext

        @Override
        NettyClientCustomizer specializeForChannel(Channel channel, ChannelRole role) {
            return new NettyClientCustomizer() {
                @Override
                NettyClientCustomizer specializeForChannel(Channel channel_, ChannelRole role_) {
                    return FailingCustomizer.this.specializeForChannel(channel_, role_)
                }

                @Override
                void onRequestPipelineBuilt() {
                    if (failNext) {
                        failNext = false
                        throw new IllegalStateException(MESSAGE)
                    }
                }
            }
        }
    }

    @Requires(property = "spec.name", value = "RequestPipelineFailureReleasesConnectionSpec")
    @Controller("/pipeline-failure")
    static class TestController {

        @Post(value = "/echo", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String echo(@Body String body) {
            body
        }

        @Get("/ok")
        String ok() {
            "ok"
        }
    }
}

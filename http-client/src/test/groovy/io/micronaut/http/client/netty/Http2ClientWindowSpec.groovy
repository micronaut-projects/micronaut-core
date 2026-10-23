package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClientConfiguration
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http2.Http2CodecUtil
import io.netty.handler.codec.http2.Http2Connection
import spock.lang.Specification

/**
 * Checks the flow-control window the client advertises, for each of the three places the frame
 * codec is installed.
 */
class Http2ClientWindowSpec extends Specification {
    private static final int MIB = 1024 * 1024

    /**
     * The rule netty's {@code Http2FrameCodec.tryExpandConnectionFlowControlWindow} applies: the
     * connection window is raised by twice the amount by which the stream window exceeds the
     * protocol default.
     */
    private static int connectionWindowFor(int streamWindow) {
        return Http2CodecUtil.DEFAULT_WINDOW_SIZE + 2 * (streamWindow - Http2CodecUtil.DEFAULT_WINDOW_SIZE)
    }

    private static void patch(DefaultHttpClient client, ConnectionManagerSpec.EmbeddedTestConnectionBase connection) {
        connection.clientChannel = new EmbeddedChannel(new DummyChannelId('client'), connection.clientInitializer)
        ChannelPromise openFuture = connection.clientChannel.newPromise()
        openFuture.setSuccess()
        client.connectionManager = new EmbeddedConnectionManager(client.connectionManager, [connection.clientChannel], [openFuture])
    }

    def 'the window reaches the server over #mode (initial-window-size=#window)'(String mode, Integer window, int expectedStreamWindow) {
        given:
        def properties = ['micronaut.http.client.ssl.insecure-trust-all-certificates': (Object) true]
        if (mode == 'h2c upgrade') {
            properties['micronaut.http.client.plaintext-mode'] = 'h2c'
        } else if (mode == 'h2c prior knowledge') {
            properties['micronaut.http.client.plaintext-mode'] = 'h2c_prior_knowledge'
        }
        if (window != null) {
            properties['micronaut.http.client.http2.initial-window-size'] = window
        }
        def ctx = ApplicationContext.run(properties)
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new ConnectionManagerSpec.EmbeddedTestConnectionHttp2()
        switch (mode) {
            case 'tls':
                conn.setupHttp2Tls()
                break
            default:
                conn.setupH2c()
                break
        }
        patch(client, conn)

        when:
        def future = conn.testExchangeRequest(client)
        if (mode == 'h2c upgrade') {
            conn.exchangeH2c()
        } else {
            conn.exchangeSettings()
        }
        conn.testExchangeResponse(future)

        then:
        Http2Connection connection = conn.serverFrameCodec.connection()
        connection.remote().flowController().initialWindowSize() == expectedStreamWindow
        connection.remote().flowController().windowSize(connection.connectionStream()) == connectionWindowFor(expectedStreamWindow)

        cleanup:
        client.close()
        ctx.close()

        where:
        mode                  | window
        'tls'                 | null
        'h2c upgrade'         | null
        'tls'                 | MIB
        'h2c upgrade'         | MIB
        'tls'                 | Http2CodecUtil.DEFAULT_WINDOW_SIZE
        'h2c upgrade'         | Http2CodecUtil.DEFAULT_WINDOW_SIZE

        expectedStreamWindow = window == null ? HttpClientConfiguration.Http2ClientConfiguration.DEFAULT_INITIAL_WINDOW_SIZE : window
    }

    def 'the default is larger than the protocol default'() {
        expect:
        HttpClientConfiguration.Http2ClientConfiguration.DEFAULT_INITIAL_WINDOW_SIZE > Http2CodecUtil.DEFAULT_WINDOW_SIZE
    }

    def 'a negative initial window size is rejected'() {
        when:
        new HttpClientConfiguration.Http2ClientConfiguration().initialWindowSize = -1

        then:
        thrown IllegalArgumentException
    }
}

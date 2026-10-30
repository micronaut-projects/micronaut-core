package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClientConfiguration
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http2.Http2CodecUtil
import io.netty.handler.codec.http2.Http2Connection
import spock.lang.Specification

/**
 * Checks the flow-control windows the client advertises, for each of the places the frame codec
 * is installed.
 */
class Http2ClientWindowSpec extends Specification {
    private static final int MIB = 1024 * 1024

    /**
     * The window the connection as a whole ends up with: raised by twice the amount by which the
     * stream window exceeds the protocol default, without overflowing past the maximum.
     */
    private static int connectionWindowFor(int streamWindow) {
        long derived = Http2CodecUtil.DEFAULT_WINDOW_SIZE + 2L * (streamWindow - Http2CodecUtil.DEFAULT_WINDOW_SIZE)
        return (int) Math.min(Http2CodecUtil.MAX_INITIAL_WINDOW_SIZE, derived)
    }

    private static void patch(DefaultHttpClient client, ConnectionManagerSpec.EmbeddedTestConnectionBase connection) {
        connection.clientChannel = new EmbeddedChannel(new DummyChannelId('client'), connection.clientInitializer)
        ChannelPromise openFuture = connection.clientChannel.newPromise()
        openFuture.setSuccess()
        client.connectionManager = new EmbeddedConnectionManager(client.connectionManager, [connection.clientChannel], [openFuture])
    }

    private static Http2Connection exchange(ConnectionManagerSpec.EmbeddedTestConnectionHttp2 conn,
                                            DefaultHttpClient client,
                                            String mode) {
        def future = conn.testExchangeRequest(client)
        if (mode == 'h2c upgrade') {
            conn.exchangeH2c()
        } else {
            conn.exchangeSettings()
        }
        conn.testExchangeResponse(future)
        return conn.serverFrameCodec.connection()
    }

    def 'the window reaches the server over #mode (initial-window-size=#window)'(String mode, Integer window, int expectedStreamWindow) {
        given:
        def properties = ['micronaut.http.client.ssl.insecure-trust-all-certificates': (Object) true]
        if (mode == 'h2c upgrade') {
            properties['micronaut.http.client.plaintext-mode'] = 'h2c'
        }
        if (window != null) {
            properties['micronaut.http.client.http2.initial-window-size'] = window
        }
        def ctx = ApplicationContext.run(properties)
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new ConnectionManagerSpec.EmbeddedTestConnectionHttp2()
        if (mode == 'tls') {
            conn.setupHttp2Tls()
        } else {
            conn.setupH2c()
        }
        patch(client, conn)

        when:
        Http2Connection connection = exchange(conn, client, mode)

        then:
        connection.remote().flowController().initialWindowSize() == expectedStreamWindow
        connection.remote().flowController().windowSize(connection.connectionStream()) == connectionWindowFor(expectedStreamWindow)

        cleanup:
        client.close()
        ctx.close()

        where:
        mode          | window
        'tls'         | null
        'h2c upgrade' | null
        'tls'         | MIB
        'h2c upgrade' | MIB
        'tls'         | Http2CodecUtil.DEFAULT_WINDOW_SIZE
        'h2c upgrade' | Http2CodecUtil.DEFAULT_WINDOW_SIZE
        // just above the protocol default: the derived connection window is less than twice the
        // old one, so netty alone would hold the WINDOW_UPDATE back until the server had used
        // part of the old window
        'tls'         | 80000
        'h2c upgrade' | 80000

        expectedStreamWindow = window == null ? HttpClientConfiguration.Http2ClientConfiguration.DEFAULT_INITIAL_WINDOW_SIZE : window
    }

    def 'initial-connection-window-size raises the connection window over #mode'(String mode) {
        given:
        def properties = [
                'micronaut.http.client.ssl.insecure-trust-all-certificates'              : (Object) true,
                'micronaut.http.client.http2.initial-window-size'                        : Http2CodecUtil.DEFAULT_WINDOW_SIZE,
                'micronaut.http.client.http2.initial-connection-window-size'             : 4 * MIB,
        ]
        if (mode == 'h2c upgrade') {
            properties['micronaut.http.client.plaintext-mode'] = 'h2c'
        }
        def ctx = ApplicationContext.run(properties)
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new ConnectionManagerSpec.EmbeddedTestConnectionHttp2()
        if (mode == 'tls') {
            conn.setupHttp2Tls()
        } else {
            conn.setupH2c()
        }
        patch(client, conn)

        when:
        Http2Connection connection = exchange(conn, client, mode)

        then:
        'the stream window is left at the protocol default'
        connection.remote().flowController().initialWindowSize() == Http2CodecUtil.DEFAULT_WINDOW_SIZE
        and:
        'the connection window is the configured one, not the derived one'
        connection.remote().flowController().windowSize(connection.connectionStream()) == 4 * MIB

        cleanup:
        client.close()
        ctx.close()

        where:
        mode << ['tls', 'h2c upgrade']
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

    def 'an unset initial window size leaves the setting to netty'() {
        when:
        def config = new HttpClientConfiguration.Http2ClientConfiguration()
        config.initialWindowSize = null

        then:
        config.initialWindowSize == null
    }

    def 'a connection window below the protocol default is rejected'() {
        when:
        new HttpClientConfiguration.Http2ClientConfiguration().initialConnectionWindowSize = Http2CodecUtil.DEFAULT_WINDOW_SIZE - 1

        then:
        thrown IllegalArgumentException
    }
}

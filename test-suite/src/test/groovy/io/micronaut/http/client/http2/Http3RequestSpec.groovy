package io.micronaut.http.client.http2

class Http3RequestSpec extends Http2RequestSpec {
    @Override
    Map config() {
        return super.config() + [
                "micronaut.http.client.alpn-modes" : ["h3"],
                'micronaut.server.netty.listeners.a.family': 'QUIC',
        ]
    }
}

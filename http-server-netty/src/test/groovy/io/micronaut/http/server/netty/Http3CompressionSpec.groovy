package io.micronaut.http.server.netty

class Http3CompressionSpec extends CompressionSpec {
    @Override
    protected Map<String, Object> serverOptions() {
        return [
                'micronaut.http.client.alpn-modes': 'h3',
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,

                'micronaut.server.ssl.enabled': true,
                'micronaut.server.ssl.build-self-signed': true,
                'micronaut.server.netty.listeners.http3.family': "QUIC",
        ]
    }
}

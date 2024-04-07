package io.micronaut.http.server.netty

class Http2CompressionSpec extends CompressionSpec {
    @Override
    protected Map<String, Object> serverOptions() {
        [
                'micronaut.http.client.alpn-modes': 'h2',
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,

                'micronaut.server.http-version': '2.0',
                'micronaut.server.ssl.enabled': true,
                'micronaut.server.ssl.build-self-signed': true,
        ] as Map<String, Object>
    }
}

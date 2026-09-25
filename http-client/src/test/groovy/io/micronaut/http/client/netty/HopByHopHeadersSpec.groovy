package io.micronaut.http.client.netty

import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.HttpHeaders
import spock.lang.Specification

class HopByHopHeadersSpec extends Specification {

    void "the hop-by-hop headers are removed from Netty headers"() {
        given:
        HttpHeaders headers = new DefaultHttpHeaders()
            .add("Connection", "keep-alive, X-Hop")
            .add("Connection", "x-other-hop,")
            .add("X-Hop", "1")
            .add("X-Other-Hop", "2")
            .add("Keep-Alive", "timeout=5")
            .add("Proxy-Authorization", "Basic abc")
            .add("proxy-connection", "keep-alive")
            .add("TE", "trailers")
            .add("Trailer", "X-Checksum")
            .add("Transfer-Encoding", "chunked")
            .add("Upgrade", "h2c")
            .add("Accept", "text/plain")
            .add("Accept", "application/json")
            .add("X-Proxyish", "kept")

        when:
        HopByHopHeaders.strip(headers)

        then:
        headers.names().toList().sort() == ["Accept", "X-Proxyish"]
        headers.getAll("Accept") == ["text/plain", "application/json"]
    }
}

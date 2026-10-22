package io.micronaut.http.server.util;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.util.HttpHeadersUtil;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForwardedHeadersHopByHopTest {

    @Test
    void forwardingHeadersListedInConnectionSurviveStripAfterApply() {
        String connection = "X-Forwarded-For, X-Forwarded-Proto, Forwarded";
        MutableHttpRequest<Object> received = HttpRequest.GET("/orders");
        received.header(HttpHeaders.HOST, "gw.example");
        received.header(HttpHeaders.CONNECTION, connection);
        InetSocketAddress address = new InetSocketAddress("203.0.113.7", 50000);
        HttpRequest<?> inbound = new HttpRequestWrapper<Object>(received) {
            @Override
            public InetSocketAddress getRemoteAddress() {
                return address;
            }
        };
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders")
            .header(HttpHeaders.CONNECTION, connection);

        ForwardedHeaders.apply(inbound, outbound);
        HttpHeadersUtil.stripHopByHopHeaders(outbound.getHeaders());

        assertEquals(List.of("203.0.113.7"), outbound.getHeaders().getAll(ForwardedHeaders.X_FORWARDED_FOR));
        assertEquals("http", outbound.getHeaders().get(ForwardedHeaders.X_FORWARDED_PROTO));
        assertEquals(List.of("for=203.0.113.7;proto=http;host=gw.example"), outbound.getHeaders().getAll(HttpHeaders.FORWARDED));
    }
}

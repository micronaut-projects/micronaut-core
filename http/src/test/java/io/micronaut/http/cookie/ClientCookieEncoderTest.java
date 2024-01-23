package io.micronaut.http.cookie;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ClientCookieEncoderTest {

    @Test
    void clientCookieEncoderResolvedViaSpi() {
        assertInstanceOf(DefaultClientCookieEncoder.class, ClientCookieEncoder.INSTANCE);
    }
}

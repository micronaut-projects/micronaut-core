package io.micronaut.http.cookie;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ServerCookieEncoderTest {

    @Test
    void serverCookieEncoderResolvedViaSpi() {
        assertInstanceOf(DefaultServerCookieEncoder.class, ServerCookieEncoder.INSTANCE);
    }
}

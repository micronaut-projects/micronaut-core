package io.micronaut.http.cookie;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ServerCookieDecoderTest {

    @Test
    void serverCookieDecoderResolvedViaSpi() {
        assertInstanceOf(ServerCookieDecoder.class, ServerCookieDecoder.INSTANCE);
    }
}

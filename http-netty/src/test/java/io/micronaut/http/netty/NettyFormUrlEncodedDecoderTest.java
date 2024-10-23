package io.micronaut.http.netty;

import io.micronaut.http.form.FormUrlEncodedDecoder;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(startApplication = false)
class NettyFormUrlEncodedDecoderTest {

    @Inject
    FormUrlEncodedDecoder formUrlEncodedDecoder;

    @Test
    void nettyFormUrlEncodedDecoderTakesPrecedence() {
        assertInstanceOf(NettyFormUrlEncodedDecoder.class, formUrlEncodedDecoder);
    }

    @Test
    void formUrlEncodedStringDecodingIntoMap() {
        String csrfToken = "abcde";
        String body = "username=sherlock&csrfToken=" + csrfToken + "&password=elementary";
        Map<String, Object> m = formUrlEncodedDecoder.decode(body, StandardCharsets.UTF_8);
        assertNotNull(m);
        assertEquals(Set.of("username", "csrfToken", "password"), m.keySet());
        assertEquals("sherlock", m.get("username"));
        assertEquals("elementary", m.get("password"));
        assertEquals(csrfToken, m.get("csrfToken"));
    }
}

package io.micronaut.http.form;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest(startApplication = false)
class FormUrlEncodedDecoderTest {

    @Inject
    FormUrlEncodedDecoder decoder;

    @Test
    void formUrlEncodedStringDecodingIntoMap() {
        String csrfToken = "abcde";
        String body = "username=sherlock&csrfToken=" + csrfToken + "&password=elementary";
        Map<String, Object> m = decoder.decode(body, StandardCharsets.UTF_8);
        assertNotNull(m);
        assertEquals(Set.of("username", "csrfToken", "password"), m.keySet());
        assertEquals("sherlock", m.get("username"));
        assertEquals("elementary", m.get("password"));
        assertEquals(csrfToken, m.get("csrfToken"));
    }
}

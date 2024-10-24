package io.micronaut.http.form;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

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

        body = "key1=value1&key1=value2&key1=value3&singleKey=singleValue&emptyKey=";
        m = decoder.decode(body, StandardCharsets.UTF_8);
        assertNotNull(m);
        assertEquals(Set.of("key1", "singleKey", "emptyKey"), m.keySet());
        assertEquals(List.of("value1", "value2", "value3"), m.get("key1"));
        assertEquals("singleValue", m.get("singleKey"));
        assertNull(m.get("emptyKey"));
    }
}

package io.micronaut.http.form;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "micronaut.http.forms.semicolon-is-normal-char", value = "true")
@MicronautTest(startApplication = false)
class FormUrlEncodedDecoderSemicolonTest {

    @Inject
    FormUrlEncodedDecoder decoder;

    @Test
    void ampersandSeparatesFieldsWhenSemicolonIsNormalChar() {
        Map<String, Object> m = decoder.decode("username=sherlock;holmes&csrfToken=abcde", StandardCharsets.UTF_8);
        assertEquals(Map.of("username", "sherlock;holmes", "csrfToken", "abcde"), m);
    }
}

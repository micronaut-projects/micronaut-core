package io.micronaut.core.io.value;

import io.micronaut.core.io.ResourceLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValueResourceLoaderTest {
    @Test
    public void string() throws IOException {
        ResourceLoader loader = StringResourceLoader.getInstance();

        try (InputStream stream = loader.getResource("string:foo").orElseThrow().openStream()) {
            assertEquals("foo", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        try (InputStream stream = loader.getResourceAsStream("string:foo").orElseThrow()) {
            assertEquals("foo", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }

        assertThrows(IllegalArgumentException.class, () -> loader.getResource("not-string"));
    }

    @Test
    public void base64() throws IOException {
        ResourceLoader loader = Base64ResourceLoader.getInstance();

        try (InputStream stream = loader.getResourceAsStream("base64:" + Base64.getEncoder().encodeToString("foo".getBytes(StandardCharsets.UTF_8))).orElseThrow()) {
            assertEquals("foo", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}

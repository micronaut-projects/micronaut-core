package io.micronaut.docs.http.server.response.textplain;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.math.BigDecimal;
import java.util.Calendar;

@Property(name = "spec.name", value = "TextPlainControllerTest")
@MicronautTest
public class TextPlainControllerTest {

    @Client("/txt")
    @Inject
    HttpClient httpClient;

    @Test
    void textPlainBoolean() {
        final String result = httpClient.toBlocking().retrieve("/boolean");

        Assertions.assertEquals("true", result);
    }

    @Test
    void textPlainMonoBoolean() {
        final String result = httpClient.toBlocking().retrieve("/boolean/mono");

        Assertions.assertEquals("true", result);
    }

    @Test
    void textPlainFluxBoolean() {
        final String result = httpClient.toBlocking().retrieve("/boolean/flux");

        Assertions.assertEquals("true", result);
    }

    @Test
    void textPlainBigDecimal() {
        final String result = httpClient.toBlocking().retrieve("/bigdecimal");

        Assertions.assertEquals(BigDecimal.valueOf(Long.MAX_VALUE).toString(), result);
    }

    @Test
    void textPlainDate() {
        final String result = httpClient.toBlocking().retrieve("/date");

        Assertions.assertEquals(new Calendar.Builder().setDate(2023,7,4).build().toString(), result);
    }

    @Test
    void textPlainPerson() {
        final String result = httpClient.toBlocking().retrieve("/person");

        Assertions.assertEquals(new Person("Dean Wette", 65).toString(), result);
    }
}

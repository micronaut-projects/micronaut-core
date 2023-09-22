package io.micronaut.docs.http.server.response.textplain;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "spec.name", value = "TextPlainControllerTest")
@MicronautTest
class TextPlainControllerTest {

    @Client("/txt")
    @Inject
    HttpClient httpClient;

    @Test
    void textPlainBoolean() {
        asserTextResult("/boolean", "true");
    }

    @Test
    void textPlainMonoBoolean() {
        asserTextResult("/boolean/mono", "true");
    }

    @Test
    void textPlainFluxBoolean() {
        asserTextResult("/boolean/flux", "true");
    }

    @Test
    void textPlainBigDecimal() {
        asserTextResult("/bigdecimal", BigDecimal.valueOf(Long.MAX_VALUE).toString());
    }

    @Test
    void textPlainDate() {
        asserTextResult("/date", new Calendar.Builder().setDate(2023,7,4).build().toString());
    }

    @Test
    void textPlainPerson() {
        asserTextResult("/person", new Person("Dean Wette", 65).toString());
    }

    private void asserTextResult(String url, String expectedResult) {
        final String result = httpClient.toBlocking().retrieve(url);
        assertEquals(expectedResult, result);

    }
}

package io.micronaut.http.cookie;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CookieFactoryTest {

    @Test
    void cookieFactoryResolvedViaSpi() {
        assertInstanceOf(HttpCookieFactory.class, CookieFactory.INSTANCE);
    }
}

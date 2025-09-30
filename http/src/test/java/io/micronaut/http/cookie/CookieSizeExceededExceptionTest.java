package io.micronaut.http.cookie;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CookieSizeExceededExceptionTest {

    @Test
    void cookieSizeExceededExceptionTest() {
        CookieSizeExceededException ex = new CookieSizeExceededException("foo", 100, 120);
        assertEquals("The cookie [foo] byte size [120] exceeds the maximum cookie size [100]", ex.getMessage());
        assertEquals("foo", ex.getCookieName());
        assertEquals(100, ex.getMaxSize());
        assertEquals(120, ex.getSize());    }
}

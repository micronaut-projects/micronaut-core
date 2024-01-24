package io.micronaut.http.cookie;

import org.junit.jupiter.api.Test;
import java.net.HttpCookie;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookieComparatorTest {

    @Test
    void cookieComparator() {
        Cookie cookie1 = new CookieHttpCookieAdapter(new HttpCookie("SID", "31d4d96e407aad42")).path("/foo");
        Cookie cookie2 = new CookieHttpCookieAdapter(new HttpCookie("SID", "31d4d96e407aad42")).path("/foo/bar");
        assertTrue(cookie1.compareTo(cookie2) < 0);
    }
}

package io.micronaut.http.netty.cookies;

import io.micronaut.http.cookie.SameSite;
import io.netty.handler.codec.http.cookie.CookieHeaderNames;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NettyCookieSameSiteTest {

    @ParameterizedTest
    @EnumSource(SameSite.class)
    void sameSiteMapsToTheNettyConstantOfTheSameName(SameSite sameSite) {
        DefaultCookie nettyCookie = new DefaultCookie("name", "value");
        NettyCookie cookie = new NettyCookie(nettyCookie);

        cookie.sameSite(sameSite);

        assertEquals(CookieHeaderNames.SameSite.valueOf(sameSite.name()), nettyCookie.sameSite());
        assertEquals(Optional.of(sameSite), cookie.getSameSite());
    }

    @Test
    void nullSameSiteClearsIt() {
        DefaultCookie nettyCookie = new DefaultCookie("name", "value");
        nettyCookie.setSameSite(CookieHeaderNames.SameSite.Strict);
        NettyCookie cookie = new NettyCookie(nettyCookie);

        cookie.sameSite(null);

        assertNull(nettyCookie.sameSite());
        assertEquals(Optional.empty(), cookie.getSameSite());
    }
}

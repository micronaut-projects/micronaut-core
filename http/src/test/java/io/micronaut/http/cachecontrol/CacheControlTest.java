package io.micronaut.http.cachecontrol;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheControlTest {

    @Test
    void testToString() {
        CacheControl cacheControl = CacheControl.builder()
            .publicDirective()
                .maxAge(Duration.ofDays(365))
                .inmutable()
                .build();
        assertEquals("public, max-age=31536000, immutable", cacheControl.toString());

        cacheControl = CacheControl.builder()
                .noStore()
                .build();
        assertEquals("no-store", cacheControl.toString());

        cacheControl = CacheControl.builder()
                .noCache()
                .mustRevalidate()
                .build();
        assertEquals("no-cache, must-revalidate", cacheControl.toString());

        cacheControl = CacheControl.builder()
                .noCache()
                .proxyRevalidate()
                .build();
        assertEquals("no-cache, proxy-revalidate", cacheControl.toString());

        cacheControl = CacheControl.builder()
            .privateDirective()
            .build();
        assertEquals("private", cacheControl.toString());

        cacheControl = CacheControl.builder()
            .mustUnderstand()
            .noStore()
            .build();
        assertEquals("must-understand, no-store", cacheControl.toString());

        cacheControl = CacheControl.builder()
            .noTransform()
            .build();
        assertEquals("no-transform", cacheControl.toString());

        cacheControl = CacheControl.builder()
                .publicDirective()
                .maxAge(Duration.ofMinutes(5))
                .sMaxAge(Duration.ofMinutes(10))
                .build();
        assertEquals("public, max-age=300, s-maxage=600", cacheControl.toString());

        cacheControl = CacheControl.builder()
            .maxAge(Duration.ofDays(7))
            .staleWhileRevalidate(Duration.ofDays(1))
            .build();
        assertEquals("max-age=604800, stale-while-revalidate=86400", cacheControl.toString());

        cacheControl = CacheControl.builder()
            .maxAge(Duration.ofDays(7))
            .staleIfError(Duration.ofDays(1))
            .build();
        assertEquals("max-age=604800, stale-if-error=86400", cacheControl.toString());
    }
}

package io.micronaut.web.router.uri;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

class UriUtilIsValidPathTest {
    @Test
    void validPaths() throws Exception {
        for (String s : List.of("/", "/foo", "/foo/bar?a=1&b=2", "/p%20q", "/p?a=%41&b=%e2%82%ac", "/%2F?%3D=%3d")) {
            Assertions.assertTrue(UriUtil.isValidPath(s), s);
            Assertions.assertEquals(s, new URI(s).toString(), s);
            Assertions.assertEquals(s, UriUtil.toValidPath(s), s);
        }
    }

    @Test
    void invalidPaths() {
        for (String s : List.of("", "foo", "//foo", "/a//b", "/..", "/./x", "/p?a=%", "/p?a=%4", "/p?a=%zz",
            "/p?a=%4g", "/p%", "/p%%41", "/p?a=|", "/p#f", "http://example.com/p", "/p?a=é")) {
            Assertions.assertFalse(UriUtil.isValidPath(s), s);
        }
    }
}

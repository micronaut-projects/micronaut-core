package io.micronaut.web.router.uri;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.junit.jupiter.api.Assertions;

import java.net.URI;
import java.net.URISyntaxException;

class UriUtilTest {
    @FuzzTest(maxDuration = "30m")
    void whatwgUrlCanBeFixedUp(FuzzedDataProvider data) throws URISyntaxException {
        String input = data.consumeRemainingAsString();
        WhatwgParser parser = new WhatwgParser(input);
        parser.setBaseUrl(new WhatwgUrl("http", "", "", "example.com", null, "/", false, null, null));
        try {
            parser.parse();
        } catch (IllegalArgumentException e) {
            return;
        }
        WhatwgUrl url = parser.toUrl();
        StringBuilder builder = new StringBuilder();
        builder.append(url.path);
        if (url.query != null) {
            builder.append("?").append(url.query);
        }
        String valid = UriUtil.toValidPath(builder.toString());
        URI uri = new URI(valid); // should not throw
        Assertions.assertEquals(url.query == null, uri.getRawQuery() == null);
    }

    @FuzzTest(maxDuration = "30m")
    void validPaths(FuzzedDataProvider data) throws URISyntaxException {
        String input = data.consumeRemainingAsString();
        if (!UriUtil.isValidPath(input)) {
            return;
        }
        URI uri = new URI(input); // should not throw

        Assertions.assertFalse(uri.isAbsolute());

        WhatwgParser parser = new WhatwgParser(input);
        parser.setBaseUrl(new WhatwgUrl("http", "", "", "example.com", null, "/", false, null, null));
        try {
            parser.parse();
        } catch (IllegalArgumentException e) {
            return;
        }
        WhatwgUrl url = parser.toUrl();

        Assertions.assertEquals("example.com", url.host); // check that this really is a relative URI
        Assertions.assertEquals(url.query == null, uri.getRawQuery() == null);
    }

    @FuzzTest(maxDuration = "30m")
    void isRelative(FuzzedDataProvider data) {
        String input = data.consumeRemainingAsString();

        WhatwgParser parser = new WhatwgParser(input);
        parser.setBaseUrl(new WhatwgUrl("http", "", "", "example.com", null, "/", false, null, null));

        boolean relative = UriUtil.isRelative(input);
        if (relative) {
            try {
                parser.parse();
            } catch (IllegalArgumentException e) {
                return;
            }

            Assertions.assertEquals("http", parser.toUrl().scheme); // all other fields can still change in a relative uri
        } else {
            parser.setStateOverride(WhatwgParser.State.SCHEME_START);
            try {
                parser.parse();
            } catch (IllegalArgumentException e) {
                if (e.getMessage().equals("Invalid scheme while state override is given") || e.getMessage().equals("Invalid character in scheme")) {
                    throw e;
                }
                return;
            }
        }

        if (relative) {
            try {
                URI uri = new URI(UriUtil.toValidPath(input));
                Assertions.assertFalse(uri.isAbsolute());
            } catch (URISyntaxException ignored) {
            }
        }

        URI uri;
        try {
            uri = new URI(input);
        } catch (URISyntaxException e) {
            return;
        }
        Assertions.assertEquals(uri.isAbsolute(), !relative);
    }
}

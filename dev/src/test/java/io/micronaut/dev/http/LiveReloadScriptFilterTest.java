package io.micronaut.dev.http;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveReloadScriptFilterTest {

    @Test
    void theScriptGoesBeforeTheBodyEndWhateverItsCase() {
        String injected = LiveReloadScriptFilter.inject("<html><body><p>hi</p></BODY></html>", 35729);
        assertTrue(injected.contains("<p>hi</p><script src=\"http://localhost:35729/livereload.js?snipver=1\" async></script></BODY>"));
        assertEquals(1, injected.split("livereload.js").length - 1);
    }

    @Test
    void aPageWithoutABodyIsLeftAlone() {
        assertNull(LiveReloadScriptFilter.inject("<p>fragment</p>", 35729));
    }

    @Test
    void theLengthAndTransferCodingOfTheReplacedBodyDescribeThePageWithTheScript() {
        String page = "<html><body>caf\u00e9</body></html>";
        String injected = LiveReloadScriptFilter.inject(page, 35729);
        byte[] expected = injected.getBytes(StandardCharsets.UTF_8);

        MutableHttpResponse<?> text = HttpResponse.ok(page).contentType(MediaType.TEXT_HTML_TYPE).contentLength(page.length());
        text.header(HttpHeaders.TRANSFER_ENCODING, "chunked");
        LiveReloadScriptFilter.replaceBody(text, injected, StandardCharsets.UTF_8);
        // sent as the bytes counted, in the charset the content type now declares
        assertArrayEquals(expected, (byte[]) text.body());
        assertEquals(StandardCharsets.UTF_8, text.getContentType().orElseThrow().getCharset().orElseThrow());
        assertEquals(expected.length, text.getHeaders().contentLength().orElseThrow());
        assertFalse(text.getHeaders().contains(HttpHeaders.TRANSFER_ENCODING));

        byte[] original = page.getBytes(StandardCharsets.UTF_8);
        MutableHttpResponse<?> bytes = HttpResponse.ok(original).contentType(MediaType.TEXT_HTML_TYPE).contentLength(original.length);
        LiveReloadScriptFilter.replaceBody(bytes, injected, StandardCharsets.UTF_8);
        assertArrayEquals(expected, (byte[]) bytes.body());
        assertEquals(expected.length, bytes.getHeaders().contentLength().orElseThrow());
    }

    @Test
    void anEncodedBodyIsRecognisedSoThatTheScriptIsNotSplicedIntoIt() {
        assertTrue(LiveReloadScriptFilter.isEncoded(HttpResponse.ok().header(HttpHeaders.CONTENT_ENCODING, "gzip")));
        assertFalse(LiveReloadScriptFilter.isEncoded(HttpResponse.ok().header(HttpHeaders.CONTENT_ENCODING, "identity")));
        assertFalse(LiveReloadScriptFilter.isEncoded(HttpResponse.ok()));
    }
}

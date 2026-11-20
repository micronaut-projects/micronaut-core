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
    void itSeesTheResponseAfterEveryOtherFilterTheHeaderPopulatorIncluded() {
        // the response-header populator, which may add a policy, has the order FIRST.before(); a tie would leave which of
        // the two sees the response last to the order the beans are found in
        LiveReloadScriptFilter filter = new LiveReloadScriptFilter(null, null, null, null);
        assertTrue(filter.getOrder() < io.micronaut.http.filter.ServerFilterPhase.FIRST.before());
    }

    @Test
    void theScriptGoesBeforeTheBodyEndWhateverItsCase() {
        String injected = LiveReloadScriptFilter.inject("<html><body><p>hi</p></BODY></html>", 35729);
        assertTrue(injected.contains("<p>hi</p><script src=\"http://localhost:35729/livereload.js?snipver=1\" async></script></BODY>"));
        assertEquals(1, injected.split("livereload.js").length - 1);
    }

    @Test
    void theScriptGoesBeforeTheBodyEndOfAPageWhoseLowerCaseIsLonger() {
        // İ is two characters in lower case: an index found in a lower-case copy would fall before the body end
        String page = "<html><body><div>İİİ</div></body></html>";
        String tag = "<script src=\"http://localhost:35729/livereload.js?snipver=1\" async></script>";
        assertEquals("<html><body><div>İİİ</div>" + tag + "</body></html>", LiveReloadScriptFilter.inject(page, 35729));
        assertEquals("<p>İ</p>".length(), LiveReloadScriptFilter.bodyEnd("<p>İ</p></BoDy>"));
        assertEquals(-1, LiveReloadScriptFilter.bodyEnd("<p>İ</p></bod"));
    }

    @Test
    void aPageWithoutABodyIsLeftAlone() {
        assertNull(LiveReloadScriptFilter.inject("<p>fragment</p>", 35729));
    }

    @Test
    void aPageDeclaringAContentSecurityPolicyInAMetaElementIsLeftAlone() {
        // the policy would block the script, as one sent in the header would
        assertNull(LiveReloadScriptFilter.inject("<html><head><meta http-equiv=\"Content-Security-Policy\" content=\"script-src 'self'\"></head><body></body></html>", 35729));
        assertNull(LiveReloadScriptFilter.inject("<html><head><META content=\"default-src 'self'\" HTTP-EQUIV='content-security-policy'/></head><body></body></html>", 35729));
        assertNull(LiveReloadScriptFilter.inject("<html><head><meta\nhttp-equiv = content-security-policy content=\"default-src 'self'\"></head><body></body></html>", 35729));
        // a report-only policy blocks nothing, and other meta elements are no policy
        assertTrue(LiveReloadScriptFilter.inject("<html><head><meta http-equiv=\"Content-Security-Policy-Report-Only\" content=\"default-src 'self'\"></head><body></body></html>", 35729).contains("livereload.js"));
        assertTrue(LiveReloadScriptFilter.inject("<html><head><meta charset=\"utf-8\"><meta name=\"description\" content=\"Content-Security-Policy\"></head><body></body></html>", 35729).contains("livereload.js"));
        assertTrue(LiveReloadScriptFilter.inject("<html><head><meta data-http-equiv=\"content-security-policy\"></head><body></body></html>", 35729).contains("livereload.js"));
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

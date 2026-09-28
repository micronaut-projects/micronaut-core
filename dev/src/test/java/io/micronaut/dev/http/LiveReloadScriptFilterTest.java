package io.micronaut.dev.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

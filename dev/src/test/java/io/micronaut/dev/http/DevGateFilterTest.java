package io.micronaut.dev.http;

import io.micronaut.dev.CompileFailure;
import io.micronaut.dev.compile.CompileDiagnostic;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.exceptions.response.Error;
import io.micronaut.http.server.exceptions.response.HtmlErrorResponseBodyProvider;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevGateFilterTest {

    private static final Instant AT = Instant.parse("2026-10-11T10:15:30Z");
    private static final CompileFailure FAILURE = new CompileFailure(SourceKind.JAVA, List.of(
        new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "incompatible types: <String> & int", Path.of("src/main/java/app/Greeter.java"), 3, 7),
        new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "compilation aborted", null, 0, 0)
    ), AT);

    @Test
    void aClientThatIsNotABrowserReceivesTheDiagnosticsAsJson() {
        HttpResponse<?> response = DevGateFilter.respond(HttpRequest.GET("/greet").accept(MediaType.APPLICATION_JSON_TYPE), FAILURE, null);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getContentType().orElseThrow());
        Map<?, ?> body = (Map<?, ?>) response.body();
        assertEquals("Compilation failed", body.get(DevGateFilter.KEY_MESSAGE));
        assertEquals("java", body.get(DevGateFilter.KEY_KIND));
        assertEquals(AT.toString(), body.get(DevGateFilter.KEY_AT));
        List<?> diagnostics = (List<?>) body.get(DevGateFilter.KEY_DIAGNOSTICS);
        assertEquals(2, diagnostics.size());
        Map<?, ?> located = (Map<?, ?>) diagnostics.get(0);
        assertEquals("ERROR", located.get(DevGateFilter.KEY_SEVERITY));
        assertEquals(Path.of("src/main/java/app/Greeter.java").toString(), located.get(DevGateFilter.KEY_FILE));
        assertEquals(3L, located.get(DevGateFilter.KEY_LINE));
        assertEquals(7L, located.get(DevGateFilter.KEY_COLUMN));
        // a diagnostic without a file has no position
        Map<?, ?> unlocated = (Map<?, ?>) diagnostics.get(1);
        assertEquals("compilation aborted", unlocated.get(DevGateFilter.KEY_MESSAGE));
        assertFalse(unlocated.containsKey(DevGateFilter.KEY_FILE));
    }

    @Test
    void aBrowserWithoutTheServersErrorPageReceivesTheBuiltInPage() {
        HttpResponse<?> response = DevGateFilter.respond(HttpRequest.GET("/greet").accept(MediaType.TEXT_HTML_TYPE), FAILURE, null);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatus());
        assertEquals(MediaType.TEXT_HTML_TYPE, response.getContentType().orElseThrow());
        String page = (String) response.body();
        assertEquals(DevGateFilter.page(FAILURE), page);
        assertTrue(page.contains("Fix the java sources"), page);
        // the diagnostics are escaped
        assertTrue(page.contains("incompatible types: &lt;String&gt; &amp; int"), page);
        assertTrue(page.contains(Path.of("src/main/java/app/Greeter.java") + ":3"), page);
        assertTrue(page.contains("ERROR: compilation aborted"), page);
    }

    @Test
    void aBrowserReceivesTheServersErrorPageWhenThereIsOne() {
        HtmlErrorResponseBodyProvider provider = (context, response) -> response.getStatus().getCode() + "\n"
            + context.getErrors().stream().map(Error::getMessage).collect(Collectors.joining("\n"));
        DevErrorPage errorPage = new DevErrorPage(provider);
        HttpResponse<?> response = DevGateFilter.respond(HttpRequest.GET("/greet").accept(MediaType.TEXT_HTML_TYPE), FAILURE, errorPage);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatus());
        assertEquals("""
            503
            The java sources do not compile; the previous version keeps running until they do.
            ERROR: %s:3: incompatible types: <String> & int
            ERROR: compilation aborted""".formatted(Path.of("src/main/java/app/Greeter.java")), response.body());
    }
}

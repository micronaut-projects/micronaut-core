package io.micronaut.http.util;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HtmlEntityEncodingHtmlSanitizerTest {
    @Test
    void sanitize() {
        HtmlEntityEncodingHtmlSanitizer sanitizer = new HtmlEntityEncodingHtmlSanitizer();
        String html = sanitizer.sanitize("<b>Hello, World!</b>");
        assertEquals("&lt;b&gt;Hello, World!&lt;/b&gt;", html);

        html = sanitizer.sanitize("\"Hello, World!\"");
        assertEquals("&quot;Hello, World!&quot;", html);
        html = sanitizer.sanitize("'Hello, World!'");
        assertEquals("&#x27;Hello, World!&#x27;", html);
        assertEquals("", sanitizer.sanitize(null));
    }

    @Test
    void beanOfHtmlSanitizerExistsAndItDefaultsToHtmlEntityEncodingHtmlSanitizer() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            assertTrue(ctx.containsBean(HtmlSanitizer.class));
            assertTrue(ctx.getBean(HtmlSanitizer.class) instanceof HtmlEntityEncodingHtmlSanitizer);
        }
    }

    @Test
    void itIsEasyToProvideYourOwnBeanOfTypeHtmlSanitizer() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "HtmlSanitizerReplacement"))) {
            assertTrue(ctx.containsBean(HtmlSanitizer.class));
            assertTrue(ctx.getBean(HtmlSanitizer.class) instanceof BogusHtmlSanitizer);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "HtmlSanitizerReplacement")
    static class BogusHtmlSanitizer implements HtmlSanitizer {
        @Override
        public String sanitize(String html) {
            return "Bogus";
        }
    }
}

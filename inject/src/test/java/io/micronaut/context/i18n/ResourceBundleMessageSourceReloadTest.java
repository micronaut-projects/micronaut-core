package io.micronaut.context.i18n;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ResourceBundleMessageSource#reload()} has the source read its bundle again, as a development runtime asks
 * when a file of the bundle changed, and a default bundle that is gone leaves the lookups without a message.
 */
class ResourceBundleMessageSourceReloadTest {

    @TempDir
    Path resources;

    @Test
    void reloadReadsTheEditedBundle() throws Exception {
        Path dir = Files.createDirectories(resources.resolve("reload"));
        Files.writeString(dir.resolve("messages.properties"), "greeting=one\n");
        Files.writeString(dir.resolve("messages_de.properties"), "greeting=eins\n");
        withLoader(() -> {
            ResourceBundleMessageSource source = new ResourceBundleMessageSource("reload.messages", Locale.ENGLISH);
            assertEquals(Optional.of("one"), source.getMessage("greeting", Locale.ENGLISH));
            assertEquals(Optional.of("eins"), source.getMessage("greeting", Locale.GERMAN));

            Files.writeString(dir.resolve("messages.properties"), "greeting=two\n");
            Files.writeString(dir.resolve("messages_de.properties"), "greeting=zwei\n");
            // cached until reloaded
            assertEquals(Optional.of("one"), source.getMessage("greeting", Locale.ENGLISH));

            source.reload();
            assertEquals(Optional.of("two"), source.getMessage("greeting", Locale.ENGLISH));
            assertEquals(Optional.of("zwei"), source.getMessage("greeting", Locale.GERMAN));

            Files.delete(dir.resolve("messages.properties"));
            Files.delete(dir.resolve("messages_de.properties"));
            source.reload();
            assertEquals(Optional.empty(), source.getMessage("greeting", Locale.ENGLISH));
            assertEquals(Optional.empty(), source.getMessage("greeting", Locale.GERMAN));
        });
    }

    private void withLoader(Action action) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {resources.toUri().toURL()}, null)) {
            thread.setContextClassLoader(loader);
            action.run();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private interface Action {
        void run() throws Exception;
    }
}

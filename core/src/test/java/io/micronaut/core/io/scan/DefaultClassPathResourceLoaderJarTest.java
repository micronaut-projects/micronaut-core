package io.micronaut.core.io.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reading resources out of a jar, which is served by the jar entry fast path rather than by a zip file system.
 */
class DefaultClassPathResourceLoaderJarTest {

    @TempDir
    Path tempDir;

    @Test
    void readsAnEntryOfAJar() throws IOException {
        try (URLClassLoader classLoader = jarClassLoader()) {
            DefaultClassPathResourceLoader loader = new DefaultClassPathResourceLoader(classLoader);

            Optional<InputStream> stream = loader.getResourceAsStream("conf/app.properties");

            assertTrue(stream.isPresent());
            try (InputStream input = stream.get()) {
                assertEquals("name=test", new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertTrue(loader.getResource("conf/app.properties").isPresent());
        }
    }

    @Test
    void readsAnEntryOfAJarUnderABasePath() throws IOException {
        try (URLClassLoader classLoader = jarClassLoader()) {
            DefaultClassPathResourceLoader loader = new DefaultClassPathResourceLoader(classLoader, "conf");

            Optional<InputStream> stream = loader.getResourceAsStream("app.properties");

            assertTrue(stream.isPresent());
            try (InputStream input = stream.get()) {
                assertEquals("name=test", new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void doesNotReadADirectoryOfAJarAsAStream() throws IOException {
        try (URLClassLoader classLoader = jarClassLoader()) {
            DefaultClassPathResourceLoader loader = new DefaultClassPathResourceLoader(classLoader);

            assertFalse(loader.getResourceAsStream("conf").isPresent());
        }
    }

    @Test
    void answersEmptyForAMissingEntry() throws IOException {
        try (URLClassLoader classLoader = jarClassLoader()) {
            DefaultClassPathResourceLoader loader = new DefaultClassPathResourceLoader(classLoader);

            assertFalse(loader.getResourceAsStream("conf/missing.properties").isPresent());
        }
    }

    private URLClassLoader jarClassLoader() throws IOException {
        Path jar = tempDir.resolve("resources.jar");
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String directory : List.of("META-INF/", "conf/")) {
                zip.putNextEntry(new ZipEntry(directory));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("conf/app.properties"));
            zip.write("name=test".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return new URLClassLoader(new URL[]{jar.toUri().toURL()}, null);
    }
}

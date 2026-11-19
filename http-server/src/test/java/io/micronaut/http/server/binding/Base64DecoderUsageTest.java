package io.micronaut.http.server.binding;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java4Shell (CVE-2026-114938): this module's main sources must decode Base64 through
 * {@code io.micronaut.core.util.Base64Utils}, never with a JDK decoder. The pattern is tested in the
 * core module's Base64DecoderUsageTest.
 */
class Base64DecoderUsageTest {

    private static final Pattern JDK_DECODER = Pattern.compile("\\bget(Url|Mime)?Decoder\\s*\\(|::\\s*get(Url|Mime)?Decoder\\b");

    @Test
    void mainSourcesDecodeBase64OnlyThroughBase64Utils() throws IOException {
        List<Path> sources;
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            sources = files.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertTrue(sources.stream().anyMatch(p -> p.endsWith("BasicAuthArgumentBinder.java")),
                "BasicAuthArgumentBinder.java not found: the scan does not cover the http-server main sources");

        List<String> offenders = sources.stream()
                .filter(p -> JDK_DECODER.matcher(read(p)).find())
                .map(Path::toString)
                .toList();

        assertEquals(List.of(), offenders, "Use Base64Utils.decode or Base64Utils.decodeUrl instead of the JDK Base64 decoders");
    }

    private static String read(Path source) {
        try {
            return Files.readString(source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

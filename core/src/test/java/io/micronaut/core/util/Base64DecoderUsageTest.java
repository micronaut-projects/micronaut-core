package io.micronaut.core.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java4Shell (CVE-2026-114938): every JDK Base64 decode in main sources must go through {@link Base64Utils},
 * which rejects the payloads that reach the vulnerable intrinsic. The call site tests cannot tell the guard
 * from the JDK decoder on an unaffected JDK, so this test fails instead when a call site bypasses the guard.
 *
 * <p>It scans only this module's main sources, relative to the project directory Gradle runs tests in, so the
 * sources it reads are compiled into this task's inputs. http and http-server have their own copy.</p>
 */
class Base64DecoderUsageTest {

    /**
     * A call such as {@code Base64.getDecoder()} or a method reference such as {@code Base64::getUrlDecoder}.
     * A decoder obtained in a way that never spells its factory method (reflection, or a third-party class) is
     * outside what a source scan can see.
     */
    private static final Pattern JDK_DECODER = Pattern.compile("\\bget(Url|Mime)?Decoder\\s*\\(|::\\s*get(Url|Mime)?Decoder\\b");
    private static final List<String> SOURCE_ROOTS = List.of("java", "kotlin", "groovy");
    private static final String ALLOWED = "Base64Utils.java";

    @Test
    void mainSourcesDecodeBase64OnlyThroughBase64Utils() {
        List<Path> sources = mainSources();
        assertTrue(sources.stream().anyMatch(p -> p.endsWith(Path.of("io", "micronaut", "core", "util", ALLOWED))),
                "Base64Utils.java not found: the scan does not cover the core module's main sources");

        List<String> offenders = sources.stream()
                .filter(p -> !p.getFileName().toString().equals(ALLOWED))
                .filter(Base64DecoderUsageTest::usesJdkDecoder)
                .map(Path::toString)
                .toList();

        assertEquals(List.of(), offenders, "Use Base64Utils.decode or Base64Utils.decodeUrl instead of the JDK Base64 decoders");
    }

    @Test
    void patternMatchesTheJdkDecoders() {
        assertTrue(JDK_DECODER.matcher("Base64.getDecoder().decode(s)").find());
        assertTrue(JDK_DECODER.matcher("java.util.Base64.getUrlDecoder().decode(s)").find());
        assertTrue(JDK_DECODER.matcher("getMimeDecoder ()").find());
        assertTrue(JDK_DECODER.matcher("Supplier<Base64.Decoder> s = Base64::getUrlDecoder;").find());
        assertTrue(JDK_DECODER.matcher("Optional.of(java.util.Base64:: getDecoder)").find());
        assertTrue(JDK_DECODER.matcher("Base64::getMimeDecoder)").find());
        assertFalse(JDK_DECODER.matcher("Base64.getEncoder().encodeToString(b)").find());
        assertFalse(JDK_DECODER.matcher("Base64Utils.decodeUrl(s)").find());
        assertFalse(JDK_DECODER.matcher("Base64Utils::decodeUrl").find());
        assertFalse(JDK_DECODER.matcher("Base64::getUrlEncoder").find());
    }

    private static boolean usesJdkDecoder(Path source) {
        try {
            return JDK_DECODER.matcher(Files.readString(source)).find();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> mainSources() {
        return SOURCE_ROOTS.stream()
                .map(lang -> Path.of("src", "main", lang))
                .filter(Files::isDirectory)
                .flatMap(Base64DecoderUsageTest::walk)
                .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".kt") || p.toString().endsWith(".groovy"))
                .toList();
    }

    private static Stream<Path> walk(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

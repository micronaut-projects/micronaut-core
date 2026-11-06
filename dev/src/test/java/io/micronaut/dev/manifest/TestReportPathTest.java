package io.micronaut.dev.manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestReportPathTest {

    @TempDir
    Path directory;

    @Test
    void theTestReportIsServedAtTestsUnlessThePathIsSet() {
        assertEquals("/tests/", manifest(null).testSettings().htmlReportPath());
        assertEquals("/reports/tests/", manifest("reports/tests").testSettings().htmlReportPath());
        assertEquals("/pyronaut/", manifest(" /pyronaut ").testSettings().htmlReportPath());
        assertEquals("/a.b_c~d-e/0Z/", manifest("/a.b_c~d-e/0Z/").testSettings().htmlReportPath());
        assertEquals("/.../", manifest("...").testSettings().htmlReportPath());
    }

    @Test
    void settingsWithoutAPathServeTheReportAtTests() {
        TestSettings settings = new TestSettings("junit", true, false, false, directory.resolve("reports"), directory.resolve("report"), List.of(), Map.of());
        assertEquals("/tests/", settings.htmlReportPath());
    }

    @Test
    void aPathOutsidePlainSegmentsOrOfTheServerItselfIsRejected() {
        for (String path : new String[] {"/", "/../etc/", "/a/./b/", "/a b/", "/micronaut-dev/events/", "/livereload.js", "/livereload/assets/", "/tests/?x=1", "//", "/a//b/", "/tests/..", "/./", "/t%20s/"}) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> manifest(path), path);
            assertTrue(failure.getMessage().contains("test report"), failure.getMessage());
        }
    }

    private DevManifest manifest(String path) {
        Properties properties = new Properties();
        properties.setProperty("micronaut.dev.main-class", "example.Application");
        properties.setProperty("micronaut.dev.reloadable", "build/classes");
        if (path != null) {
            properties.setProperty("micronaut.dev.test.html-report-path", path);
        }
        return DevManifest.of(directory, properties);
    }
}
